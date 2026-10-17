import json
import httpx
import pytest
from langgraph.checkpoint.sqlite.aio import AsyncSqliteSaver
from ai_service.infrastructure.workflows import ReturnApprovalWorkflow
from ai_service.infrastructure.commerce import HttpCommerceGateway
from ai_service.domain import Principal, ServiceError

ALICE = Principal('alice','demo',frozenset({'CUSTOMER'}))
MANAGER = Principal('manager','demo',frozenset({'MANAGER'}))
OTHER = Principal('bob','other',frozenset({'MANAGER'}))
RETURN_ID = '22222222-2222-4222-8222-222222222222'
BODY = {'orderId':'11111111-1111-4111-8111-111111111111','reason':'Unopened'}


async def test_restart_retry_gateway_authority_and_no_credentials(tmp_path):
    db = tmp_path/'workflow.db'
    status = 'PENDING'
    effects = 0
    fail_once = True
    requests = []
    def handler(request):
        nonlocal status, effects, fail_once
        requests.append((request.method, request.url.path, request.headers['authorization'],request.headers.get('idempotency-key')))
        if request.url.path == '/api/v1/returns':
            return httpx.Response(201,json={'id':'22222222-2222-4222-8222-222222222222','status':status,'version':0})
        if request.method == 'GET':
            return httpx.Response(200,json={'id':'22222222-2222-4222-8222-222222222222','status':status,'version':0})
        assert request.headers['authorization'] == 'Bearer manager-secret'
        if request.url.path.endswith('/approve'):
            status = 'APPROVED'
            return httpx.Response(200,json={'id':RETURN_ID,'status':status,'version':1})
        if request.url.path.endswith('/execute'):
            assert status == 'APPROVED'
            status = 'EXECUTED'
            effects += 1
            # The backend committed, but response was lost.
            if fail_once:
                fail_once = False
                raise httpx.ReadTimeout('response lost', request=request)
            return httpx.Response(200,json={'id':RETURN_ID,'status':status,'version':1})
        pytest.fail('Unexpected call')
    async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as client:
        gateway = HttpCommerceGateway(client,'http://commerce')
        async with AsyncSqliteSaver.from_conn_string(str(db)) as saver:
            await saver.setup()
            workflows = ReturnApprovalWorkflow(gateway,saver)
            first = await workflows.create(ALICE,'customer-secret',BODY,'same','corr')
            assert first['status'] == 'PENDING'
            assert await workflows.create(ALICE,'customer-secret',BODY,'same','corr') == first
            with pytest.raises(ServiceError,match='different'):
                await workflows.create(ALICE,'customer-secret',{**BODY,'reason':'Changed'},'same','corr')
        async with AsyncSqliteSaver.from_conn_string(str(db)) as saver:
            workflows = ReturnApprovalWorkflow(gateway,saver)
            wid = first['workflowId']
            assert (await workflows.get(wid,ALICE))['status'] == 'PENDING'
            with pytest.raises(ServiceError) as e: await workflows.get(wid,OTHER)
            assert e.value.status == 404
            with pytest.raises(ServiceError) as e: await workflows.approve(wid,ALICE,'customer-secret','corr')
            assert e.value.status == 403
            with pytest.raises(ServiceError,match='unavailable'):
                await workflows.approve(wid,MANAGER,'manager-secret','corr')
            assert (await workflows.approve(wid,MANAGER,'manager-secret','corr'))['status'] == 'EXECUTED'
            assert (await workflows.approve(wid,MANAGER,'manager-secret','corr'))['status'] == 'EXECUTED'
            assert effects == 1
            checkpoint_bytes = db.read_bytes()
            assert b'customer-secret' not in checkpoint_bytes
            assert b'manager-secret' not in checkpoint_bytes
            execute = [r for r in requests if r[1].endswith('/execute')]
            assert execute[0][3] == 'execute:' + first['returnId']


async def test_backend_expiry_between_approval_and_execution_is_not_bypassed(tmp_path):
    def handler(request):
        if request.url.path == '/api/v1/returns':
            return httpx.Response(201,json={'id':RETURN_ID,'status':'PENDING','version':0})
        if request.method == 'GET': return httpx.Response(200,json={'id':RETURN_ID,'status':'APPROVED','version':0})
        if request.url.path.endswith('/approve'):
            return httpx.Response(200,json={'id':RETURN_ID,'status':'APPROVED','version':1})
        assert request.url.path.endswith('/execute')
        return httpx.Response(409,json={'detail':'expired'})
    async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as client, AsyncSqliteSaver.from_conn_string(str(tmp_path/'db')) as saver:
        w = ReturnApprovalWorkflow(HttpCommerceGateway(client,'http://commerce'),saver)
        first = await w.create(ALICE,'x',BODY,'k','c')
        with pytest.raises(ServiceError) as e:
            await w.approve(first['workflowId'],MANAGER,'m','c')
        assert e.value.status == 409


async def test_expired_approval_can_be_renewed_by_explicit_manager_after_restart(tmp_path):
    db = str(tmp_path/'renew.db')
    approved = False
    def handler(request):
        nonlocal approved
        if request.url.path == '/api/v1/returns':
            return httpx.Response(201,json={'id':RETURN_ID,'version':0,'status':'PENDING'})
        if request.method == 'GET':
            return httpx.Response(200,json={'id':RETURN_ID,'version':2,'status':'APPROVED',
                                          'approvedUntil':'2000-01-01T00:00:00Z'})
        if request.url.path.endswith('/approve'):
            assert json.loads(request.content)['version'] == 2
            approved = True
            return httpx.Response(200,json={'id':RETURN_ID,'status':'APPROVED','version':3})
        assert approved
        assert json.loads(request.content)['version'] == 3
        return httpx.Response(200,json={'id':RETURN_ID,'status':'EXECUTED','version':4})
    async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as client:
        gateway = HttpCommerceGateway(client,'http://commerce')
        async with AsyncSqliteSaver.from_conn_string(db) as saver:
            w = ReturnApprovalWorkflow(gateway,saver)
            created = await w.create(ALICE,'customer',BODY,'renew','corr')
        async with AsyncSqliteSaver.from_conn_string(db) as saver:
            w = ReturnApprovalWorkflow(gateway,saver)
            result = await w.approve(created['workflowId'],MANAGER,'manager','corr')
            assert result['status'] == 'EXECUTED'
            assert approved


@pytest.mark.parametrize('interruption', ['timeout', 'cancel'])
async def test_lost_proposal_response_restarts_with_same_key_and_fresh_token(tmp_path, interruption):
    import asyncio
    started = asyncio.Event()
    db = tmp_path/'proposal.db'
    requests = []
    async def handler(request):
        requests.append(request)
        assert request.url.path == '/api/v1/returns'
        if len(requests) == 1:
            if interruption == 'cancel':
                started.set()
                await asyncio.Event().wait()
            raise httpx.ReadTimeout('response lost', request=request)
        return httpx.Response(201, json={'id':RETURN_ID,'status':'PENDING','version':0})
    async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as client:
        gateway = HttpCommerceGateway(client, 'http://commerce')
        async with AsyncSqliteSaver.from_conn_string(str(db)) as saver:
            workflow = ReturnApprovalWorkflow(gateway, saver)
            task = asyncio.create_task(workflow.create(ALICE, 'old-customer-secret', BODY, 'durable-key', 'c'))
            if interruption == 'cancel':
                await asyncio.wait_for(started.wait(), timeout=2)
                task.cancel()
            with pytest.raises(asyncio.CancelledError if interruption == 'cancel' else ServiceError):
                await task
        async with AsyncSqliteSaver.from_conn_string(str(db)) as saver:
            workflow = ReturnApprovalWorkflow(gateway, saver)
            result = await workflow.create(ALICE, 'fresh-customer-secret', BODY, 'durable-key', 'c')
            assert result['status'] == 'PENDING'
            snapshot = await workflow.graph.aget_state(workflow.config(result['workflowId']))
            assert snapshot.next == ('approval',)
        assert [r.headers['idempotency-key'] for r in requests] == ['proposal:' + result['workflowId']] * 2
        assert requests[1].headers['authorization'] == 'Bearer fresh-customer-secret'
        assert b'customer-secret' not in db.read_bytes()


@pytest.mark.parametrize('interruption', ['timeout', 'cancel'])
async def test_execution_restart_reuses_key_and_backend_version(tmp_path, interruption):
    import asyncio
    started = asyncio.Event()
    db = tmp_path/'execution.db'
    execute_requests = []
    effects = 0
    version = 0
    async def handler(request):
        nonlocal effects, version
        if request.url.path == '/api/v1/returns':
            return httpx.Response(201, json={'id':RETURN_ID,'status':'PENDING','version':version})
        if request.method == 'GET':
            return httpx.Response(200, json={'id':RETURN_ID,'status':'APPROVED','version':version})
        if request.url.path.endswith('/approve'):
            assert json.loads(request.content)['version'] == version
            version += 1
            return httpx.Response(200, json={'id':RETURN_ID,'status':'APPROVED','version':version})
        execute_requests.append(request)
        assert json.loads(request.content)['version'] == version
        if len(execute_requests) == 1:
            if interruption == 'cancel':
                started.set()
                await asyncio.Event().wait()
            raise httpx.ReadTimeout('not committed', request=request)
        effects += 1
        return httpx.Response(200, json={'id':RETURN_ID,'status':'EXECUTED','version':version + 1})
    async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as client:
        gateway = HttpCommerceGateway(client, 'http://commerce')
        async with AsyncSqliteSaver.from_conn_string(str(db)) as saver:
            workflow = ReturnApprovalWorkflow(gateway, saver)
            created = await workflow.create(ALICE, 'customer-secret', BODY, 'key', 'c')
            task = asyncio.create_task(workflow.approve(created['workflowId'], MANAGER, 'old-manager-secret', 'c'))
            if interruption == 'cancel':
                await asyncio.wait_for(started.wait(), timeout=2)
                task.cancel()
            with pytest.raises(asyncio.CancelledError if interruption == 'cancel' else ServiceError):
                await task
        async with AsyncSqliteSaver.from_conn_string(str(db)) as saver:
            workflow = ReturnApprovalWorkflow(gateway, saver)
            result = await workflow.approve(created['workflowId'], MANAGER, 'fresh-manager-secret', 'c')
            assert result['status'] == 'EXECUTED'
            assert await workflow.approve(created['workflowId'], MANAGER, 'fresh-manager-secret', 'c') == result
        assert effects == 1
        assert len(execute_requests) == 2
        assert {r.headers['idempotency-key'] for r in execute_requests} == {'execute:' + RETURN_ID}
        assert execute_requests[1].headers['authorization'] == 'Bearer fresh-manager-secret'
        assert b'manager-secret' not in db.read_bytes()


async def test_executed_response_during_approval_skips_execution(tmp_path):
    calls = []
    async def handler(request):
        calls.append(request.url.path)
        if request.url.path == '/api/v1/returns':
            return httpx.Response(201, json={'id':RETURN_ID,'status':'PENDING','version':0})
        if request.method == 'GET':
            return httpx.Response(200, json={'id':RETURN_ID,'status':'APPROVED','version':1})
        assert request.url.path.endswith('/approve')
        return httpx.Response(200, json={'id':RETURN_ID,'status':'EXECUTED','version':2})
    async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as client, AsyncSqliteSaver.from_conn_string(str(tmp_path/'db')) as saver:
        workflow = ReturnApprovalWorkflow(HttpCommerceGateway(client, 'http://commerce'), saver)
        created = await workflow.create(ALICE, 'customer', BODY, 'key', 'c')
        assert (await workflow.approve(created['workflowId'], MANAGER, 'manager', 'c'))['status'] == 'EXECUTED'
    assert len(calls) == 3


async def test_workflow_admission_deadline_includes_queue_and_releases_capacity(tmp_path):
    import asyncio
    from ai_service.application.ports import ReturnResult

    started = asyncio.Event()
    cancelled = asyncio.Event()
    calls = []

    class BlockedGateway:
        async def create_return_proposal(self, **kwargs):
            calls.append(kwargs['idempotency_key'])
            started.set()
            try:
                await asyncio.Event().wait()
            finally:
                cancelled.set()

    async with AsyncSqliteSaver.from_conn_string(str(tmp_path/'bounded.db')) as saver:
        workflow = ReturnApprovalWorkflow(BlockedGateway(), saver,
                                          operation_timeout=0.15, max_operations=2)
        first = asyncio.create_task(workflow.create(ALICE, 'ephemeral', BODY, 'one', 'c'))
        await asyncio.wait_for(started.wait(), 2)
        # Queue a different tenant. Its deadline must include time awaiting the lock.
        second = asyncio.create_task(workflow.create(OTHER, 'ephemeral', BODY, 'two', 'c'))
        await asyncio.sleep(0)
        assert workflow.in_flight == 2
        with pytest.raises(ServiceError, match='capacity') as rejected:
            await workflow.create(ALICE, 'ephemeral', BODY, 'three', 'c')
        assert rejected.value.status == 503
        results = await asyncio.wait_for(asyncio.gather(first, second, return_exceptions=True), 2)
        assert all(isinstance(result, ServiceError) and result.status == 503 for result in results)
        assert all('deadline' in result.detail for result in results)
        assert cancelled.is_set()
        assert workflow.in_flight == 0 and not workflow.lock.locked()

        class RecoveredGateway:
            async def create_return_proposal(self, **kwargs):
                calls.append(kwargs['idempotency_key'])
                return ReturnResult(RETURN_ID, 'PENDING', 0)

        workflow.gateway = RecoveredGateway()
        recovered = await workflow.create(ALICE, 'fresh-token', BODY, 'one', 'c')
        assert recovered['status'] == 'PENDING'
        first_key = 'proposal:' + recovered['workflowId']
        assert calls.count(first_key) == 2
        assert await workflow.create(ALICE, 'fresh-token', BODY, 'one', 'c') == recovered
        assert calls.count(first_key) == 2
        assert workflow.in_flight == 0


async def test_cancelled_workflow_waiter_does_not_leak_admission_or_lock(tmp_path):
    import asyncio
    async with AsyncSqliteSaver.from_conn_string(str(tmp_path/'cancel.db')) as saver:
        workflow = ReturnApprovalWorkflow(None, saver)
        await workflow.lock.acquire()
        task = asyncio.create_task(workflow.create(ALICE, 'token', BODY, 'one', 'c'))
        await asyncio.sleep(0)
        assert workflow.in_flight == 1
        task.cancel()
        with pytest.raises(asyncio.CancelledError):
            await task
        assert workflow.in_flight == 0
        # The cancelled waiter cannot release a lock owned by someone else.
        assert workflow.lock.locked()
        workflow.lock.release()
