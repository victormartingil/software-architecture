import json
import httpx
import pytest
from ai_service.domain import Document, ServiceError
from ai_service.infrastructure.providers import OpenAIProvider

DOCS = [Document('d1','demo','Policy')]


def completed(output):
    return {'status':'completed','output':[{'content':[{'type':'output_text','text':json.dumps(output)}]}]}


async def test_real_adapter_contract_without_network():
    def handler(request):
        payload = json.loads(request.content)
        assert payload['max_output_tokens'] == 512
        assert payload['store'] is False
        assert 'tools' not in payload
        assert payload['text']['format']['strict'] is True
        return httpx.Response(200, json=completed({'answer':'Policy','citations':['d1'],'abstained':False}))
    async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as client:
        p = OpenAIProvider(client,'test-key','explicit-model',1)
        assert (await p.generate('question', DOCS))['citations'] == ['d1']
        with pytest.raises(ServiceError, match='budget'):
            await p.generate('question', DOCS)


@pytest.mark.parametrize('data', [
    {'status':'incomplete','output':[]},
    {'status':'completed','output':[{'content':[{'type':'refusal','refusal':'No'}]}]},
    completed({'unexpected':42}),
    {'status':'completed','output':[{'content':[{'type':'output_text','text':'broken JSON'}]}]},
])
async def test_invalid_refusal_incomplete(data):
    async with httpx.AsyncClient(transport=httpx.MockTransport(lambda r: httpx.Response(200,json=data))) as client:
        with pytest.raises(ServiceError):
            await OpenAIProvider(client,'key','model',1).generate('q',DOCS)


async def test_bounded_retries_breaker_and_half_open():
    count = 0
    healthy = False
    now = [0.]
    async def no_sleep(seconds): pass
    def handler(request):
        nonlocal count
        count += 1
        if healthy:
            return httpx.Response(200, json=completed({'answer':'ok','citations':['d1'],'abstained':False}))
        raise httpx.ReadTimeout('timeout', request=request)
    async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as client:
        p = OpenAIProvider(client,'key','model',10,clock=lambda:now[0],sleep=no_sleep)
        for _ in range(3):
            with pytest.raises(ServiceError): await p.generate('q',DOCS)
        assert count == 6
        with pytest.raises(ServiceError,match='circuit'): await p.generate('q',DOCS)
        assert count == 6
        now[0] = 31
        healthy = True
        assert (await p.generate('q',DOCS))['answer'] == 'ok'
        assert count == 7


async def test_old_request_completion_cannot_release_another_half_open_probe():
    import asyncio
    old_started, old_release, probe_started, probe_release = (asyncio.Event() for _ in range(4))
    now = [2.]
    async def no_sleep(seconds): pass
    async def handler(request):
        question = json.loads(json.loads(request.content)['input'])['question']
        if question == 'old':
            old_started.set()
            await old_release.wait()
            raise httpx.ReadTimeout('old call failed',request=request)
        probe_started.set()
        await probe_release.wait()
        return httpx.Response(200,json=completed({'answer':'ok','citations':['d1'],'abstained':False}))
    async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as client:
        p = OpenAIProvider(client,'key','model',10,clock=lambda:now[0],sleep=no_sleep)
        old = asyncio.create_task(p.generate('old',DOCS))
        await old_started.wait()
        # Two concurrent failures plus a subsequent elapsed open period.
        p.failures, p.open_until = 2, 1.
        probe = asyncio.create_task(p.generate('probe',DOCS))
        await probe_started.wait()
        old_release.set()
        with pytest.raises(ServiceError): await old
        assert p.probing is True
        now[0] = 40.
        with pytest.raises(ServiceError,match='circuit'):
            await p.generate('another-probe',DOCS)
        probe_release.set()
        await probe
        assert p.probing is False
