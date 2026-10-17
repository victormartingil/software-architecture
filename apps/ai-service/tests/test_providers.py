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


async def test_total_service_deadline_during_retry_counts_failure_and_opens_breaker():
    import asyncio
    from ai_service.application.answers import AnswerService
    from ai_service.domain import Principal
    calls = 0
    cancelled = 0
    class Retrieval:
        async def search(self, *args): return DOCS
    async def no_sleep(seconds): pass
    async def handler(request):
        nonlocal calls, cancelled
        calls += 1
        if calls % 2:
            raise httpx.ReadTimeout('first attempt', request=request)
        try:
            await asyncio.Event().wait()
        finally:
            cancelled += 1
    async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as client:
        provider = OpenAIProvider(client, 'key', 'model', 10, sleep=no_sleep)
        service = AnswerService(provider, Retrieval(), total_timeout=.02)
        actor = Principal('alice', 'demo', frozenset({'CUSTOMER'}))
        for expected_failures in range(1, 4):
            with pytest.raises(TimeoutError):
                await service.answer(actor, 'policy')
            assert provider.failures == expected_failures
            assert service.capacity._value == 4
        with pytest.raises(ServiceError, match='circuit'):
            await service.answer(actor, 'policy')
        assert calls == 6 and cancelled == 3


async def test_generation_budget_and_client_cancellation_are_distinct():
    import asyncio
    started = asyncio.Event()
    async def handler(request):
        started.set()
        await asyncio.Event().wait()
    async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as client:
        provider = OpenAIProvider(client, 'key', 'model', 10, generation_timeout=.02)
        with pytest.raises(ServiceError, match='generation deadline'):
            await provider.generate('q', DOCS)
        assert provider.failures == 1
        provider.generation_timeout = 1
        started.clear()
        task = asyncio.create_task(provider.generate('q', DOCS))
        await started.wait()
        task.cancel()
        with pytest.raises(asyncio.CancelledError):
            await task
        assert provider.failures == 1 and not provider.probing


async def test_old_success_cannot_close_new_half_open_period():
    import asyncio
    started, release, probe_started, probe_release = (asyncio.Event() for _ in range(4))
    now = [1.]
    async def no_sleep(seconds): pass
    async def handler(request):
        question = json.loads(json.loads(request.content)['input'])['question']
        if question == 'old':
            started.set()
            await release.wait()
        elif question == 'probe':
            probe_started.set()
            await probe_release.wait()
        else:
            raise httpx.ConnectError('offline')
        return httpx.Response(200, json=completed({'answer':'ok','citations':['d1'],'abstained':False}))
    async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as client:
        provider = OpenAIProvider(client, 'key', 'model', 20, clock=lambda: now[0], sleep=no_sleep)
        old = asyncio.create_task(provider.generate('old', DOCS))
        await started.wait()
        for _ in range(3):
            with pytest.raises(ServiceError):
                await provider.generate('fail', DOCS)
        now[0] = 32.
        probe = asyncio.create_task(provider.generate('probe', DOCS))
        await probe_started.wait()
        release.set()
        await old
        assert provider.probing and provider.open_until
        with pytest.raises(ServiceError, match='circuit'):
            await provider.generate('extra', DOCS)
        probe_release.set()
        await probe
        assert not provider.probing and provider.open_until == 0 and provider.failures == 0


async def test_embedding_adapter_contract_and_budget_without_network():
    from ai_service.infrastructure.providers import OpenAIEmbedder
    calls = 0
    def handler(request):
        nonlocal calls
        calls += 1
        assert request.url.path == '/v1/embeddings'
        assert json.loads(request.content) == {'model':'explicit-embedding', 'dimensions':3, 'input':'policy'}
        return httpx.Response(200, json={'data':[{'embedding':[1., 0., .5]}]})
    async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as client:
        embedder = OpenAIEmbedder(client, 'test-key', 'explicit-embedding', 3, 1)
        assert await embedder('policy') == [1., 0., .5]
        with pytest.raises(ServiceError, match='budget'):
            await embedder('policy')
        assert calls == 1


@pytest.mark.parametrize('failure', ['timeout', 'status', 'shape', 'empty', 'non-vector'])
async def test_embedding_failures_are_bounded_dependency_errors(failure):
    from ai_service.infrastructure.providers import OpenAIEmbedder
    def handler(request):
        if failure == 'timeout':
            raise httpx.ReadTimeout('fixture', request=request)
        if failure == 'status':
            return httpx.Response(429)
        data = {'shape':{}, 'empty':{'data':[]}, 'non-vector':{'data':[{'embedding':'invalid'}]}}
        return httpx.Response(200, json=data[failure])
    async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as client:
        embedder = OpenAIEmbedder(client, 'test-key', 'explicit-embedding', 3, 1)
        with pytest.raises(ServiceError) as error:
            await embedder('policy')
        assert error.value.status == 503 and embedder.remaining == 0
