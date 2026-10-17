import asyncio
import json

import httpx
import pytest

from ai_service.application.ports import ReturnResult
from ai_service.domain import ServiceError
from ai_service.infrastructure.commerce import HttpCommerceGateway

RETURN_ID = '22222222-2222-4222-8222-222222222222'
ORDER_ID = '11111111-1111-4111-8111-111111111111'
VALID = {'id': RETURN_ID, 'status': 'PENDING', 'version': 0}


async def invoke(gateway, operation):
    if operation == 'create':
        return await gateway.create_return_proposal(ORDER_ID, 'Unopened', 'ephemeral-token', 'proposal:key', 'corr')
    if operation == 'get':
        return await gateway.get_return(RETURN_ID, 'ephemeral-token', 'corr')
    if operation == 'approve':
        return await gateway.approve_return(RETURN_ID, 2, 'ephemeral-token', 'corr')
    return await gateway.execute_return(RETURN_ID, 3, 'ephemeral-token', 'execute:key', 'corr')


@pytest.mark.parametrize('operation,method,path,body,key,status,response_status', [
    ('create', 'POST', '/api/v1/returns', {'orderId': ORDER_ID, 'reason': 'Unopened'}, 'proposal:key', 201, 'PENDING'),
    ('get', 'GET', '/api/v1/returns/' + RETURN_ID, None, None, 200, 'PENDING'),
    ('approve', 'POST', '/api/v1/returns/' + RETURN_ID + '/approve', {'version': 2}, None, 200, 'APPROVED'),
    ('execute', 'POST', '/api/v1/returns/' + RETURN_ID + '/execute', {'version': 3}, 'execute:key', 200, 'EXECUTED'),
])
async def test_intent_operations_own_wire_contract(operation, method, path, body, key, status, response_status):
    requests = []
    def handler(request):
        requests.append(request)
        assert request.method == method
        assert request.url.path == path
        assert (json.loads(request.content) if request.content else None) == body
        assert request.headers['authorization'] == 'Bearer ephemeral-token'
        assert request.headers['x-correlation-id'] == 'corr'
        assert request.headers.get('idempotency-key') == key
        return httpx.Response(status, json={**VALID, 'status': response_status, 'version': 4})
    async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as client:
        gateway = HttpCommerceGateway(client, 'http://commerce/')
        assert await invoke(gateway, operation) == ReturnResult(RETURN_ID, response_status, 4)
        assert len(requests) == 1
        assert 'ephemeral-token' not in repr(vars(gateway))


@pytest.mark.parametrize('payload', [None, [], 'secret', 1, True, {},
    {**VALID, 'id': None}, {**VALID, 'id': 12}, {**VALID, 'id': '1-1-1-1-1'},
    {**VALID, 'id': RETURN_ID.replace('-', '')}, {**VALID, 'id': 'urn:uuid:' + RETURN_ID},
    {**VALID, 'id': ORDER_ID}, {**VALID, 'status': 'FAILED'}, {**VALID, 'status': []},
    {**VALID, 'status': None}, {**VALID, 'version': True}, {**VALID, 'version': -1},
    {**VALID, 'version': 1.0}, {**VALID, 'version': '1'}, {**VALID, 'version': None},
])
async def test_unexpected_response_is_safe_service_error(payload):
    async with httpx.AsyncClient(transport=httpx.MockTransport(lambda request: httpx.Response(200, json=payload))) as client:
        with pytest.raises(ServiceError) as error:
            await invoke(HttpCommerceGateway(client, 'http://commerce'), 'get')
        assert (error.value.status, error.value.detail) == (503, 'Commerce unavailable')
        assert 'secret' not in str(error.value)


@pytest.mark.parametrize('operation,status', [('create','APPROVED'), ('approve','PENDING'), ('execute','APPROVED')])
async def test_operation_rejects_incompatible_state(operation, status):
    async with httpx.AsyncClient(transport=httpx.MockTransport(
            lambda request: httpx.Response(201 if operation == 'create' else 200, json={**VALID, 'status': status}))) as client:
        with pytest.raises(ServiceError):
            await invoke(HttpCommerceGateway(client, 'http://commerce'), operation)


@pytest.mark.parametrize('status,expected', [(400,400),(401,401),(403,403),(404,404),(409,409),
    (422,503),(429,503),(500,503),(503,503),(301,503),(204,503),(201,503)])
async def test_http_status_is_translated_without_body_or_retry(status, expected):
    requests = []
    def handler(request):
        requests.append(request)
        return httpx.Response(status, json={'detail': 'secret backend stack trace'})
    async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as client:
        with pytest.raises(ServiceError) as error:
            await invoke(HttpCommerceGateway(client, 'http://commerce'), 'execute')
        assert error.value.status == expected
        assert 'secret' not in str(error.value)
        assert len(requests) == 1


@pytest.mark.parametrize('failure', ['invalid-json', 'timeout', 'connection', 'cancel'])
async def test_transport_failures_and_cancellation(failure):
    requests = []
    def handler(request):
        requests.append(request)
        if failure == 'invalid-json':
            return httpx.Response(200, text='secret not JSON')
        if failure == 'cancel':
            raise asyncio.CancelledError()
        if failure == 'timeout':
            raise httpx.ReadTimeout('secret', request=request)
        raise httpx.ConnectError('secret', request=request)
    async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as client:
        with pytest.raises(asyncio.CancelledError if failure == 'cancel' else ServiceError):
            await invoke(HttpCommerceGateway(client, 'http://commerce'), 'get')
        assert len(requests) == 1


async def test_complete_uppercase_uuid_is_normalized():
    identity = 'ABCDEF12-ABCD-4ABC-8ABC-ABCDEF123456'
    async with httpx.AsyncClient(transport=httpx.MockTransport(
            lambda request: httpx.Response(201, json={**VALID, 'id': identity}))) as client:
        result = await invoke(HttpCommerceGateway(client, 'http://commerce'), 'create')
        assert result.return_id == identity.lower()
