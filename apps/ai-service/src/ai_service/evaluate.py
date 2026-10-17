"""Fixed offline retrieval evaluation; never a claim about real model quality."""
import asyncio
import json
from pathlib import Path
from ai_service.infrastructure.retrieval import LexicalRetriever


def retrieval_scores(relevant: list[str], ranked: list[str]) -> dict[str, float]:
    """Per-question top-3 metrics; abstention cases are evaluated separately."""
    expected, retrieved = set(relevant), ranked[:3]
    if not expected:
        raise ValueError("Retrieval scores require at least one relevant document")
    matches = expected.intersection(retrieved)
    reciprocal_rank = next((1 / rank for rank, doc in enumerate(retrieved, 1) if doc in expected), 0.)
    return dict(recallAt3=len(matches) / len(expected), hitRateAt3=float(bool(matches)),
                mrrAt3=reciprocal_rank)


async def evaluate():
    data = Path(__file__).resolve().parents[2] / 'data'
    cases = json.loads((data/'evaluation.json').read_text())
    retriever = LexicalRetriever(data/'corpus.json')
    scores = []
    isolated = abstentions = expected_abstentions = 0
    for case in cases:
        docs = await retriever.search(case['tenant'], case['question'])
        isolated += all(d.tenant == case['tenant'] for d in docs)
        if case['relevant']:
            scores.append(retrieval_scores(case['relevant'], [d.id for d in docs]))
        if case.get('abstain'):
            expected_abstentions += 1
            abstentions += not docs
    averages = {metric: sum(score[metric] for score in scores) / len(scores)
                for metric in ('recallAt3', 'hitRateAt3', 'mrrAt3')}
    result = dict(cases=len(cases), answerable=len(scores), **averages,
                  isolation=isolated/len(cases), abstention=abstentions/expected_abstentions,
                  mode='offline-retrieval-only', realModelEvaluated=False)
    print(json.dumps(result, indent=2))
    if len(cases)<40 or result['recallAt3']<.9 or result['isolation'] != 1 or result['abstention'] != 1:
        raise SystemExit(1)


if __name__ == '__main__':
    asyncio.run(evaluate())
