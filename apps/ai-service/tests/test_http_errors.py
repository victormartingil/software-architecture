"""Framework errors follow the same wire contract as application errors."""
from uuid import UUID
import httpx
import pytest
from ai_service.main import create_app
from ai_service.infrastructure.settings import Settings


@pytest.mark.parametrize('path,status,detail', [
    ('/unknown-route?secret=never-echo-this', 404, 'Not Found'),
    ('/api/v1/answers', 405, 'Method Not Allowed'),
])
@pytest.mark.parametrize('correlation', [None, '00000000-0000-4000-8000-000000000001'])
async def test_framework_errors_keep_problem_contract(tmp_path, path, status, detail, correlation):
    app = create_app(Settings(workflow_db=str(tmp_path / 'http-errors.db')))
    headers = {'X-Correlation-ID': correlation} if correlation else {}
    async with app.router.lifespan_context(app):
        async with httpx.AsyncClient(transport=httpx.ASGITransport(app=app), base_url='http://test') as client:
            response = await client.get(path, headers=headers)
    assert response.status_code == status
    assert response.headers['content-type'] == 'application/problem+json'
    body = response.json()
    assert body == {'status': status, 'title': 'Request failed', 'detail': detail,
                    'correlationId': response.headers['X-Correlation-ID']}
    assert str(UUID(body['correlationId'])) == body['correlationId']
    if correlation:
        assert body['correlationId'] == correlation
    if status == 405:
        assert {method.strip() for method in response.headers['Allow'].split(',')} == {'POST'}
    assert 'WWW-Authenticate' not in response.headers
    assert 'never-echo-this' not in response.text


async def test_framework_http_exception_keeps_headers_but_not_sensitive_detail(tmp_path):
    from starlette.exceptions import HTTPException
    app = create_app(Settings(workflow_db=str(tmp_path / 'http-headers.db')))
    @app.get('/synthetic-http-error')
    async def fail():
        raise HTTPException(405, detail='fixture-secret-never-echo', headers={
            'Allow': 'POST', 'Retry-After': '1', 'X-Correlation-ID': 'invalid',
            'Content-Type': 'text/plain', 'Content-Length': '99999'})
    async with app.router.lifespan_context(app):
        async with httpx.AsyncClient(transport=httpx.ASGITransport(app=app), base_url='http://test') as client:
            response = await client.get('/synthetic-http-error')
    assert response.status_code == 405
    assert response.headers['Allow'] == 'POST'
    assert response.headers['Retry-After'] == '1'
    assert response.headers['content-type'] == 'application/problem+json'
    assert int(response.headers['content-length']) == len(response.content)
    assert response.json()['correlationId'] == response.headers['X-Correlation-ID']
    assert str(UUID(response.headers['X-Correlation-ID'])) == response.headers['X-Correlation-ID']
    assert 'fixture-secret' not in response.text
