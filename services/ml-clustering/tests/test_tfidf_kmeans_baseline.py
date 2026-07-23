import numpy as np
import pandas as pd
import pytest
from experiments.run_tfidf_kmeans_baseline import (
    _fit_tfidf,
    build_tech_text_corpus,
    run_kmeans_grid,
    select_best_by_silhouette,
)


@pytest.fixture
def toy_snapshot():
    df_technologies = pd.DataFrame(
        {
            "tech_id": ["t1", "t2", "t3"],
            "name": ["Python", "React", "Kubernetes"],
            "category": ["language", "frontend", "devops"],
        }
    )
    df_articles = pd.DataFrame(
        {
            "article_id": ["a1", "a2"],
            "title": ["Học Python cơ bản", "React hooks nâng cao"],
            "content": ["Python là ngôn ngữ lập trình phổ biến cho backend", "React hooks giúp quản lý state"],
        }
    )
    df_edges_mentions = pd.DataFrame({"article_id": ["a1", "a2"], "tech_id": ["t1", "t2"]})
    df_jobs = pd.DataFrame(
        {
            "job_id": ["j1", "j2"],
            "title": ["Backend Engineer", "DevOps Engineer"],
            "description": ["Viết API bằng Python Django", "Vận hành cluster Kubernetes trên AWS"],
            "requirement": ["Thành thạo Python", "Kinh nghiệm Kubernetes"],
        }
    )
    df_edges_job_requires_tech = pd.DataFrame({"job_id": ["j1", "j2"], "tech_id": ["t1", "t3"]})
    return {
        "tech_ids": ["t1", "t2", "t3"],
        "df_technologies": df_technologies,
        "df_articles": df_articles,
        "df_edges_mentions": df_edges_mentions,
        "df_jobs": df_jobs,
        "df_edges_job_requires_tech": df_edges_job_requires_tech,
    }


def test_build_tech_text_corpus_includes_article_and_job_text(toy_snapshot):
    docs = build_tech_text_corpus(
        tech_ids=toy_snapshot["tech_ids"],
        df_technologies=toy_snapshot["df_technologies"],
        df_edges_mentions=toy_snapshot["df_edges_mentions"],
        df_articles=toy_snapshot["df_articles"],
        df_edges_job_requires_tech=toy_snapshot["df_edges_job_requires_tech"],
        df_jobs=toy_snapshot["df_jobs"],
    )

    assert len(docs) == 3
    # t1 (Python): có cả article a1 lẫn job j1 → doc chứa từ khoá của cả hai nguồn
    assert "Python" in docs[0]
    assert "Django" in docs[0]
    assert "Backend" in docs[0]
    # t3 (Kubernetes): không có article nào MENTIONS, chỉ có job j2 REQUIRES
    assert "Kubernetes" in docs[2]
    assert "cluster" in docs[2]
    assert "React" not in docs[2]


def test_build_tech_text_corpus_falls_back_to_name_when_no_mentions():
    df_technologies = pd.DataFrame({"tech_id": ["t1"], "name": ["Rust"], "category": ["language"]})
    empty_articles = pd.DataFrame(columns=["article_id", "title", "content"])
    empty_edges_mentions = pd.DataFrame(columns=["article_id", "tech_id"])
    empty_jobs = pd.DataFrame(columns=["job_id", "title", "description", "requirement"])
    empty_edges_jobs = pd.DataFrame(columns=["job_id", "tech_id"])

    docs = build_tech_text_corpus(
        tech_ids=["t1"],
        df_technologies=df_technologies,
        df_edges_mentions=empty_edges_mentions,
        df_articles=empty_articles,
        df_edges_job_requires_tech=empty_edges_jobs,
        df_jobs=empty_jobs,
    )

    assert docs == ["Rust language"]


def test_fit_tfidf_falls_back_when_min_df_too_high():
    corpus = ["python backend api", "react frontend ui", "kubernetes devops cluster"]

    # min_df=5 không thể thoả với chỉ 3 document — phải tự hạ xuống min_df=1
    # thay vì raise ValueError("After pruning, no terms remain...").
    vectorizer, X = _fit_tfidf(corpus, min_df=5, max_features=100)

    assert X.shape[0] == 3
    assert X.shape[1] > 0
    assert vectorizer.min_df == 1


def test_run_kmeans_grid_skips_k_too_large_and_evaluates_related_split():
    rng = np.random.default_rng(42)
    # 2 cụm tách rõ trong không gian 4 chiều
    cluster_a = rng.normal(loc=0.0, scale=0.05, size=(6, 4))
    cluster_b = rng.normal(loc=5.0, scale=0.05, size=(6, 4))
    X = np.vstack([cluster_a, cluster_b])
    tech_ids = [f"t{i}" for i in range(12)]

    df_related = pd.DataFrame({"tech_id_a": ["t0", "t0"], "tech_id_b": ["t1", "t7"]})

    trials = run_kmeans_grid(
        X,
        tech_ids,
        n_clusters_grid=[2, 3, 50],  # 50 >= n_samples=12 → phải bị bỏ qua
        n_init=5,
        random_state=42,
        df_related=df_related,
    )

    requested_ks = {t["n_clusters_requested"] for t in trials}
    assert requested_ks == {2, 3}
    for t in trials:
        assert "related_pairs_split_ratio" in t
        assert t["n_noise"] == 0  # KMeans không có khái niệm noise


def test_select_best_by_silhouette_picks_max():
    trials = [
        {"silhouette": 0.2, "n_clusters_requested": 2},
        {"silhouette": 0.7, "n_clusters_requested": 3},
        {"silhouette": float("nan"), "n_clusters_requested": 4},
    ]
    best = select_best_by_silhouette(trials)
    assert best["n_clusters_requested"] == 3


def test_select_best_by_silhouette_raises_when_all_nan():
    trials = [{"silhouette": float("nan"), "n_clusters_requested": 2}]
    with pytest.raises(RuntimeError):
        select_best_by_silhouette(trials)
