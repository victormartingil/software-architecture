"""Fixed offline retrieval evaluation; never a claim about real model quality."""
import asyncio
import json
from pathlib import Path
from ai_service.infrastructure.retrieval import LexicalRetriever


async def evaluate():
    data = Path(__file__).resolve().parents[2] / 'data'
    cases = json.loads((data/'evaluation.json').read_text())
    retriever = LexicalRetriever(data/'corpus.json')
    hits = answerable = isolated = abstentions = expected_abstentions = 0
    for case in cases:
        docs = await retriever.search(case['tenant'],case['question'])
        isolated += all(d.tenant == case['tenant'] for d in docs)
        if case['relevant']:
            answerable += 1
            hits += bool(set(case['relevant']) & {d.id for d in docs})
        if case.get('abstain'):
            expected_abstentions += 1
            abstentions += not docs
    result = dict(cases=len(cases), answerable=answerable, recallAt3=hits/answerable,
                  isolation=isolated/len(cases),abstention=abstentions/expected_abstentions,
                  mode='offline-retrieval-only',realModelEvaluated=False)
    print(json.dumps(result,indent=2))
    if len(cases)<40 or result['recallAt3']<.9 or result['isolation'] != 1 or result['abstention'] != 1:
        raise SystemExit(1)


if __name__ == '__main__':
    asyncio.run(evaluate())
