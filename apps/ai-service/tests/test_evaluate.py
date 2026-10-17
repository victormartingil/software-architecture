from pathlib import Path
import hashlib
import json
from ai_service.evaluate import evaluation_report, validate_cases, report_path
from ai_service.infrastructure.retrieval import LexicalRetriever
import pytest
from ai_service.evaluate import retrieval_scores


def test_recall_hit_rate_and_reciprocal_rank_measure_different_guarantees():
    assert retrieval_scores(['a', 'b'], ['noise', 'b', 'other']) == {
        'recallAt3': .5, 'hitRateAt3': 1., 'mrrAt3': .5}
    assert retrieval_scores(['a', 'b'], ['b', 'a', 'other']) == {
        'recallAt3': 1., 'hitRateAt3': 1., 'mrrAt3': 1.}
    assert retrieval_scores(['a', 'b'], ['b', 'b', 'other'])['recallAt3'] == .5
    assert retrieval_scores(['a'], ['noise', 'other', 'third', 'a']) == {
        'recallAt3': 0., 'hitRateAt3': 0., 'mrrAt3': 0.}
    with pytest.raises(ValueError):
        retrieval_scores([], [])



DATA = Path(__file__).resolve().parents[1] / 'data'


async def test_report_preserves_baseline_and_exposes_lower_ranks():
    report = await evaluation_report(DATA)
    assert report['schemaVersion'] == 1
    assert report['mode'] == 'lexical'
    assert report['scope'] == 'offline-retrieval-only'
    assert report['realModelEvaluated'] is False
    assert report['datasetSha256'] == hashlib.sha256((DATA/'evaluation.json').read_bytes()).hexdigest()
    assert report['corpusSha256'] == hashlib.sha256((DATA/'corpus.json').read_bytes()).hexdigest()
    assert report['summary'] == dict(cases=42, answerable=32, recallAt3=1., hitRateAt3=1.,
                                   mrrAt3=.953125, isolation=1., abstention=1.,
                                   mode='offline-retrieval-only', realModelEvaluated=False)
    assert len({case['id'] for case in report['cases']}) == 42
    assert any(case['metrics'] and case['metrics']['mrrAt3'] < 1 for case in report['cases'])
    assert all(case['metrics'] is None for case in report['cases'] if case['abstentionExpected'])
    assert all(case['abstentionPassed'] is None for case in report['cases'] if not case['abstentionExpected'])


async def test_degraded_ranking_changes_detail_and_aggregate_together(monkeypatch):
    baseline = await evaluation_report(DATA)
    original = LexicalRetriever.search
    question = json.loads((DATA/'evaluation.json').read_text())[0]['question']

    async def degraded(self, tenant, text):
        return [] if text == question else await original(self, tenant, text)

    monkeypatch.setattr(LexicalRetriever, 'search', degraded)
    report = await evaluation_report(DATA)
    assert report['cases'][0]['metrics']['recallAt3'] == 0
    assert report['summary']['recallAt3'] < baseline['summary']['recallAt3']
    scores = [case['metrics'] for case in report['cases'] if case['metrics'] is not None]
    for metric in ('recallAt3', 'hitRateAt3', 'mrrAt3'):
        assert report['summary'][metric] == sum(score[metric] for score in scores)/len(scores)


@pytest.mark.parametrize('change', ['duplicate', 'missing', 'foreign'])
def test_dataset_rejects_invalid_ids_and_references(change):
    cases = json.loads((DATA/'evaluation.json').read_text())
    corpus = json.loads((DATA/'corpus.json').read_text())
    if change == 'duplicate':
        cases[1]['id'] = cases[0]['id']
    elif change == 'missing':
        cases[0]['relevant'] = ['absent']
    else:
        cases[0]['relevant'] = ['other-private']
    with pytest.raises(ValueError):
        validate_cases(cases, corpus)


def test_report_path_restricts_traversal_absolute_paths_and_symlinks(tmp_path):
    repository = tmp_path/'repo'
    reports = repository/'reports'
    reports.mkdir(parents=True)
    assert report_path('reports/rag.json', repository) == reports/'rag.json'
    assert report_path(str(reports/'nested/rag.json'), repository) == reports/'nested/rag.json'
    (reports/'escape').symlink_to(tmp_path, target_is_directory=True)
    (reports/'outside.json').symlink_to(tmp_path/'outside.json')
    for value in ('reports', 'reports/../bad.json', str(tmp_path/'bad.json'),
                  'reports/escape/bad.json', 'reports/outside.json'):
        with pytest.raises(ValueError):
            report_path(value, repository)
    (repository/'redirect').mkdir()
    (repository/'redirect/reports').symlink_to(reports, target_is_directory=True)
    with pytest.raises(ValueError):
        report_path('reports/bad.json', repository/'redirect')


async def test_report_is_opt_in_and_invalid_destination_creates_nothing(monkeypatch, tmp_path, capsys):
    import ai_service.evaluate as module
    monkeypatch.delenv('EVAL_REPORT', raising=False)
    monkeypatch.setattr(module, 'report_path', lambda value: (_ for _ in ()).throw(AssertionError('unexpected write')))
    await module.evaluate()
    assert json.loads(capsys.readouterr().out)['cases'] == 42
    monkeypatch.setenv('EVAL_REPORT', 'outside.json')
    monkeypatch.setattr(module, 'report_path', lambda value: report_path(value, tmp_path))
    with pytest.raises(ValueError):
        await module.evaluate()
    assert not list(tmp_path.iterdir())


async def test_failed_gate_still_writes_diagnostic_report(monkeypatch, tmp_path, capsys):
    import ai_service.evaluate as module
    report = await evaluation_report(DATA)
    report['summary']['recallAt3'] = .5

    async def degraded(data):
        return report

    monkeypatch.setattr(module, 'evaluation_report', degraded)
    monkeypatch.setenv('EVAL_REPORT', 'reports/failure.json')
    monkeypatch.setattr(module, 'report_path', lambda value: report_path(value, tmp_path))
    with pytest.raises(SystemExit) as failure:
        await module.evaluate()
    assert failure.value.code == 1
    saved = json.loads((tmp_path/'reports/failure.json').read_text())
    assert saved == report
    assert json.loads(capsys.readouterr().out) == report['summary']
