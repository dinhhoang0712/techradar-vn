"""Kiểm tra AppStore.find_nearest_known_tech — provisional lookup cho tech chưa
có trong snapshot (bằng difflib, không cần embedding model — xem app/store.py)."""

from app.store import AppStore


def _make_store(name_lower_to_id, id_to_name, tech_to_cluster):
    store = object.__new__(AppStore)  # bypass __init__ (đọc disk/MinIO)
    store.name_lower_to_id = name_lower_to_id
    store.id_to_name = id_to_name
    store.tech_to_cluster = tech_to_cluster
    return store


def test_finds_close_match_above_threshold():
    store = _make_store(
        name_lower_to_id={"kubernetes": "t1", "react": "t2"},
        id_to_name={"t1": "Kubernetes", "t2": "React"},
        tech_to_cluster={"t1": 0, "t2": 1},
    )

    result = store.find_nearest_known_tech("Kubernetess")

    assert result is not None
    matched_name, cluster_id, score = result
    assert matched_name == "Kubernetes"
    assert cluster_id == 0
    assert 0.0 < score <= 1.0


def test_returns_none_when_no_candidate_close_enough():
    store = _make_store(
        name_lower_to_id={"kubernetes": "t1"},
        id_to_name={"t1": "Kubernetes"},
        tech_to_cluster={"t1": 0},
    )

    assert store.find_nearest_known_tech("CompletelyUnrelatedWord") is None


def test_returns_none_when_best_match_is_noise_cluster():
    """Ứng viên tốt nhất lại là noise (-1) — không phải tín hiệu hữu ích, coi như
    không match được gì thay vì trả về provisional cluster -1."""
    store = _make_store(
        name_lower_to_id={"randomjunktech": "t1"},
        id_to_name={"t1": "RandomJunkTech"},
        tech_to_cluster={"t1": -1},
    )

    assert store.find_nearest_known_tech("RandomJunkTechh") is None


def test_returns_none_when_store_has_no_known_techs():
    store = _make_store(name_lower_to_id={}, id_to_name={}, tech_to_cluster={})
    assert store.find_nearest_known_tech("Anything") is None


def test_exact_case_insensitive_match_scores_perfect():
    store = _make_store(
        name_lower_to_id={"vue": "t1"},
        id_to_name={"t1": "Vue"},
        tech_to_cluster={"t1": 2},
    )

    matched_name, cluster_id, score = store.find_nearest_known_tech("VUE")

    assert matched_name == "Vue"
    assert cluster_id == 2
    assert score == 1.0
