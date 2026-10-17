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
            return httpx.Response(200,json={'status':status,'version':1})
        if request.url.path.endswith('/execute'):
            assert status == 'APPROVED'
            status = 'EXECUTED'
            effects += 1
            # The backend committed, but response was lost.
            if fail_once:
                fail_once = False
                raise httpx.ReadTimeout('response lost', request=request)
            return httpx.Response(200,json={'status':status,'version':1})
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
            return httpx.Response(201,json={'id':'r1','status':'PENDING','version':0})
        if request.method == 'GET': return httpx.Response(200,json={'status':'APPROVED','version':0})
        if request.url.path.endswith('/approve'):
            return httpx.Response(200,json={'status':'APPROVED','version':1})
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
            return httpx.Response(201,json={'id':'renew-return','version':0,'status':'PENDING'})
        if request.method == 'GET':
            return httpx.Response(200,json={'id':'renew-return','version':2,'status':'APPROVED',
                                          'approvedUntil':'2000-01-01T00:00:00Z'})
        if request.url.path.endswith('/approve'):
            assert json.loads(request.content)['version'] == 2
            approved = True
            return httpx.Response(200,json={'status':'APPROVED','version':3})
        assert approved
        assert json.loads(request.content)['version'] == 3
        return httpx.Response(200,json={'status':'EXECUTED','version':4})
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
