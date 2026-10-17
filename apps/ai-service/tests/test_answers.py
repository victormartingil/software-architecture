import asyncio
import json
from pathlib import Path
import pytest
from ai_service.application.answers import AnswerService
from ai_service.domain import Principal, ServiceError
from ai_service.infrastructure.retrieval import LexicalRetriever, VectorRetriever
from ai_service.infrastructure.providers import FakeProvider

DATA = Path(__file__).parents[1] / 'data'
DEMO = Principal('alice', 'demo', frozenset({'CUSTOMER'}))


@pytest.mark.asyncio
async def test_dataset_recall_citations_isolation():
    cases = json.loads((DATA / 'evaluation.json').read_text())
    assert len(cases) >= 40
    retrieval = LexicalRetriever(DATA / 'corpus.json')
    service = AnswerService(FakeProvider(), retrieval)
    hits = answered = 0
    for case in cases:
        actor = Principal('subject', case['tenant'], frozenset({'CUSTOMER'}))
        docs = await retrieval.search(actor.tenant, case['question'])
        assert all(d.tenant == actor.tenant for d in docs)
        if case['relevant']:
            answered += 1
            hits += bool(set(case['relevant']) & {d.id for d in docs})
        result = await service.answer(actor, case['question'])
        assert set(result['citations']) <= {d.id for d in docs}
        if case.get('abstain'):
            assert result['abstained']
        assert result['mode'] == 'fake-simulation'
    assert hits / answered >= .90


async def test_no_evidence_does_not_call_provider():
    class Never:
        mode = 'test'
        async def generate(self, *args):
            pytest.fail('AnswerProvider called without evidence')
    result = await AnswerService(Never(), LexicalRetriever(DATA / 'corpus.json')).answer(DEMO, 'asteroide cuántico')
    assert result['abstained']


async def test_invalid_citation_rejected():
    class Invalid:
        mode = 'test'
        async def generate(self, *args):
            return {'answer':'secret', 'citations':['other-private'], 'abstained':False}
    with pytest.raises(ServiceError):
        await AnswerService(Invalid(), LexicalRetriever(DATA / 'corpus.json')).answer(DEMO, 'devolución plazo')


async def test_capacity_and_cancellation_release_resources():
    class Blocking:
        mode = 'test'
        active = peak = 0
        async def generate(self, *args):
            self.active += 1
            self.peak = max(self.peak, self.active)
            try:
                await asyncio.sleep(100)
            finally:
                self.active -= 1
    provider = Blocking()
    service = AnswerService(provider, LexicalRetriever(DATA / 'corpus.json'))
    tasks = [asyncio.create_task(service.answer(DEMO, 'devolución plazo')) for _ in range(8)]
    await asyncio.sleep(.05)
    assert provider.active == provider.peak == 4
    for task in tasks:
        task.cancel()
    await asyncio.gather(*tasks, return_exceptions=True)
    assert provider.active == 0
    assert service.capacity._value == 4


async def test_embedding_dimension_rejected_before_database():
    async def embedding(q): return [1., 0.]
    with pytest.raises(ServiceError):
        await VectorRetriever('invalid', embedding, 3).search('demo', 'question')
