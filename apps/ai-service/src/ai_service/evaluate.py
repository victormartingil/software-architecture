"""Fixed offline retrieval evaluation; never a claim about real model quality."""
import asyncio
import hashlib
import json
import os
from pathlib import Path
from ai_service.infrastructure.retrieval import LexicalRetriever

REPOSITORY = Path(__file__).resolve().parents[4]
METRICS = ('recallAt3', 'hitRateAt3', 'mrrAt3')


def retrieval_scores(relevant: list[str], ranked: list[str]) -> dict[str, float]:
    """Per-question top-3 metrics; abstention cases are evaluated separately."""
    expected, retrieved = set(relevant), ranked[:3]
    if not expected:
        raise ValueError("Retrieval scores require at least one relevant document")
    matches = expected.intersection(retrieved)
    reciprocal_rank = next((1 / rank for rank, doc in enumerate(retrieved, 1) if doc in expected), 0.)
    return dict(recallAt3=len(matches) / len(expected), hitRateAt3=float(bool(matches)),
                mrrAt3=reciprocal_rank)


def report_path(value: str, repository: Path = REPOSITORY) -> Path:
    """Resolve relative to repository, refusing escapes including existing symlinks."""
    base = repository / 'reports'
    if base.resolve() != base:
        raise ValueError('reports must not redirect outside the repository')
    target = (repository / value).resolve()
    if not target.is_relative_to(base) or target == base:
        raise ValueError('Evaluation report must be a file inside reports/')
    return target


def validate_cases(cases: list[dict], documents: list[dict]) -> None:
    ids = [case.get('id') for case in cases]
    if any(not isinstance(value, str) or not value.strip() for value in ids) or len(set(ids)) != len(ids):
        raise ValueError('Evaluation case IDs must be nonempty and unique')
    corpus = {doc['id']: doc['tenant'] for doc in documents}
    if len(corpus) != len(documents):
        raise ValueError('Corpus document IDs must be unique')
    for case in cases:
        if any(doc not in corpus or corpus[doc] != case['tenant'] for doc in case['relevant']):
            raise ValueError(f"Invalid relevant document for case {case['id']}")
    if not any(case['relevant'] for case in cases) or not any(case.get('abstain') for case in cases):
        raise ValueError('Dataset must include answerable and abstention cases')


async def evaluation_report(data: Path) -> dict:
    dataset_bytes, corpus_bytes = (data/'evaluation.json').read_bytes(), (data/'corpus.json').read_bytes()
    cases, corpus = json.loads(dataset_bytes), json.loads(corpus_bytes)
    validate_cases(cases, corpus)
    retriever = LexicalRetriever(data/'corpus.json')
    details = []
    for case in cases:
        docs = await retriever.search(case['tenant'], case['question'])
        ranked = [doc.id for doc in docs]
        details.append(dict(id=case['id'], expected=case['relevant'], retrieved=ranked,
                            metrics=retrieval_scores(case['relevant'], ranked) if case['relevant'] else None,
                            isolated=all(doc.tenant == case['tenant'] for doc in docs),
                            abstentionExpected=bool(case.get('abstain')),
                            abstentionPassed=not docs if case.get('abstain') else None))
    scores = [case['metrics'] for case in details if case['metrics'] is not None]
    abstentions = [case['abstentionPassed'] for case in details if case['abstentionExpected']]
    averages = {metric: sum(score[metric] for score in scores) / len(scores) for metric in METRICS}
    summary = dict(cases=len(details), answerable=len(scores), **averages,
                   isolation=sum(case['isolated'] for case in details)/len(details),
                   abstention=sum(abstentions)/len(abstentions),
                   mode='offline-retrieval-only', realModelEvaluated=False)
    return dict(schemaVersion=1, datasetSha256=hashlib.sha256(dataset_bytes).hexdigest(),
                corpusSha256=hashlib.sha256(corpus_bytes).hexdigest(), mode='lexical',
                scope='offline-retrieval-only', realModelEvaluated=False, summary=summary, cases=details)


async def evaluate():
    # Validate destination before doing work or creating directories.
    value = os.environ.get('EVAL_REPORT', '')
    destination = report_path(value) if value else None
    report = await evaluation_report(Path(__file__).resolve().parents[2] / 'data')
    result = report['summary']
    print(json.dumps(result, indent=2))
    if destination:
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_text(json.dumps(report, indent=2) + '\n')
    if result['cases']<40 or result['recallAt3']<.9 or result['isolation'] != 1 or result['abstention'] != 1:
        raise SystemExit(1)


if __name__ == '__main__':
    asyncio.run(evaluate())
