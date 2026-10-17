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
