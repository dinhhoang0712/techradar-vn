"""Kiểm tra quy trình xử lý đặc trưng và làm sạch dữ liệu công nghệ."""

from unittest.mock import MagicMock, patch

import numpy as np
import pandas as pd
import pytest
from conf.config import NoiseFilterParams

from src.features.acronym_map import expand_tech_name
from src.features.feature_pipeline import build_feature_matrix
from src.features.noise_filter import _SHORT_TECH_WHITELIST, filter_noise
from src.features.tech_aliases import canonicalize_technology_snapshot


def test_feature_matrix_construction_shape(dummy_data, mock_feature_params):
    """Đảm bảo ma trận X được xây dựng đúng kích thước từ các nguồn dữ liệu."""
    df_tech, gds, emb, stats = dummy_data
    X, _ = build_feature_matrix(df_tech, gds, emb, stats, None, None, mock_feature_params)
    assert X.shape == (4, 2)


def test_feature_matrix_no_reduction_dimension(dummy_data, mock_feature_params):
    """Kiểm tra kích thước ma trận khi không sử dụng kỹ thuật giảm chiều."""
    mock_feature_params.reduce_dim.enabled = False
    df_tech, gds, emb, stats = dummy_data
    X, _ = build_feature_matrix(df_tech, gds, emb, stats, None, None, mock_feature_params)
    assert X.shape[1] == 6


def test_noise_filter_heuristic_removal():
    """Kiểm tra việc loại bỏ các công nghệ rác dựa trên quy tắc heuristic."""
    df = pd.DataFrame({"tech_id": ["t1", "t2"], "name": ["Python", "ab"]})
    edges = pd.DataFrame({"tech_id": ["t1", "t1"], "job_id": ["j1", "j2"]})
    params = NoiseFilterParams(enabled=True, min_job_count=2, heuristic_patterns=[r"^[a-z]{2}$"])
    result = filter_noise(df, edges, params)
    assert "ab" not in result["name"].tolist()


def test_noise_filter_whitelist_integrity():
    """Đảm bảo các công nghệ quan trọng trong Whitelist không bị bộ lọc loại bỏ."""
    assert "ai" in _SHORT_TECH_WHITELIST
    assert "ml" in _SHORT_TECH_WHITELIST


def test_technology_alias_canonicalization():
    """Kiểm tra việc hợp nhất các cách viết khác nhau của cùng một công nghệ."""
    df_tech = pd.DataFrame({"tech_id": ["t1", "t2"], "name": ["React", "React JS"]})
    df_job = pd.DataFrame({"job_id": ["j1", "j1"], "tech_id": ["t1", "t2"]})
    res = canonicalize_technology_snapshot(
        df_tech=df_tech,
        df_edges_mentions=pd.DataFrame(columns=["article_id", "tech_id"]),
        df_edges_company_uses_tech=pd.DataFrame(columns=["company_id", "tech_id"]),
        df_edges_job_requires_tech=df_job,
        df_edges_tech_related=pd.DataFrame(columns=["tech_id_a", "tech_id_b"]),
    )
    assert len(res.technologies) == 1


def test_acronym_expansion_mapping():
    """Kiểm tra tính chính xác của việc mở rộng các thuật ngữ viết tắt."""
    assert "Secure Shell" in expand_tech_name("SSH")
    assert "Kubernetes" in expand_tech_name("K8s")


def test_embed_tech_names_fallback_expands_acronyms_before_encoding():
    """acronym_map.expand_tech_name() phải được gọi TRƯỚC khi đưa vào embedding
    model — trước đây module tồn tại nhưng không được wire vào, nên "SSH" bị
    encode theo mặt chữ thay vì "SSH (Secure Shell)"."""
    from src.features import content_features

    fake_model = MagicMock()
    fake_model.encode.return_value = np.zeros((2, 768), dtype=np.float32)

    with patch.object(content_features, "_get_model", return_value=fake_model):
        content_features.embed_tech_names_fallback(["SSH", "React"])

    encoded_texts = fake_model.encode.call_args[0][0]
    assert encoded_texts[0] == "passage: SSH (Secure Shell)"
    assert encoded_texts[1] == "passage: React"


def test_build_feature_matrix_includes_skill_jaccard_and_article_temporal_blocks(dummy_data, mock_feature_params):
    """skill_jaccard/article_temporal trước đây được tính (graph_features.py)
    nhưng chưa từng được cộng vào feature matrix — giờ phải xuất hiện trong
    feature_groups khi truyền vào và flag bật."""
    df_tech, gds, emb, stats = dummy_data
    tech_ids = df_tech["tech_id"].tolist()

    skill_jaccard = pd.DataFrame(
        {
            "tech_id": tech_ids,
            "n_unique_jobs": [1, 2, 3, 4],
            "n_unique_skills_share": [1, 1, 2, 2],
            "mean_jaccard_with_top10": [0.1, 0.2, 0.3, 0.4],
        }
    )
    article_temporal = pd.DataFrame(
        {
            "tech_id": tech_ids,
            "first_mention_days_ago": [10, 20, 30, 40],
            "last_mention_days_ago": [1, 2, 3, 4],
            "mention_recency_skew": [0.0, 0.1, 0.2, 0.3],
            "mean_sentiment": [0.0, 0.1, -0.1, 0.2],
        }
    )

    mock_feature_params.reduce_dim.enabled = False
    X, meta = build_feature_matrix(
        df_tech,
        gds,
        emb,
        stats,
        None,
        None,
        mock_feature_params,
        skill_jaccard=skill_jaccard,
        article_temporal=article_temporal,
    )

    assert "skill_jaccard" in meta.feature_groups
    assert "article_temporal" in meta.feature_groups
    # 6 cột cũ (graph_stats=1 + pagerank=1 + fastrp=2 + content_emb=2, xem
    # test_feature_matrix_no_reduction_dimension) + 3 skill_jaccard + 4 article_temporal
    assert X.shape[1] == 6 + 3 + 4


def test_build_feature_matrix_skips_new_blocks_when_disabled(dummy_data, mock_feature_params):
    """Nếu use_skill_jaccard/use_article_temporal_stats=false thì dù có truyền
    DataFrame vào cũng không được cộng vào ma trận."""
    df_tech, gds, emb, stats = dummy_data
    tech_ids = df_tech["tech_id"].tolist()
    skill_jaccard = pd.DataFrame({"tech_id": tech_ids, "n_unique_jobs": [1, 2, 3, 4]})

    mock_feature_params.reduce_dim.enabled = False
    mock_feature_params.use_skill_jaccard = False
    X, meta = build_feature_matrix(
        df_tech,
        gds,
        emb,
        stats,
        None,
        None,
        mock_feature_params,
        skill_jaccard=skill_jaccard,
        article_temporal=None,
    )
    assert "skill_jaccard" not in meta.feature_groups
    assert X.shape[1] == 6


def test_build_client_node2vec_embedding_connects_and_isolates_correctly():
    """Node2Vec client-side (networkx + gensim) thay thế GDS đã tắt trên AuraDB
    free tier: tech nối qua job chung phải có vector khác 0, tech cô lập phải
    ra zero-vector."""
    pytest.importorskip("networkx")
    pytest.importorskip("node2vec")
    from src.features.graph_features import build_client_node2vec_embedding

    df_tech = pd.DataFrame({"tech_id": ["t1", "t2", "t3"]})
    df_mentions = pd.DataFrame({"article_id": ["a1"], "tech_id": ["t1"]})
    df_uses = pd.DataFrame(columns=["company_id", "tech_id"])
    df_requires = pd.DataFrame({"job_id": ["j1", "j1"], "tech_id": ["t1", "t2"]})
    df_related = pd.DataFrame(columns=["tech_id_a", "tech_id_b"])

    result = build_client_node2vec_embedding(
        df_technologies=df_tech,
        df_edges_article_mentions_tech=df_mentions,
        df_edges_company_uses_tech=df_uses,
        df_edges_job_requires_tech=df_requires,
        df_edges_tech_related_tech=df_related,
        embedding_dim=4,
        walk_length=3,
        walks_per_node=2,
        window=2,
    )

    assert list(result["tech_id"]) == ["t1", "t2", "t3"]
    emb_cols = [c for c in result.columns if c.startswith("node2vec_")]
    assert len(emb_cols) == 4

    t3_vec = result.loc[result["tech_id"] == "t3", emb_cols].values[0]
    assert np.allclose(t3_vec, 0.0)  # tech cô lập, không có cạnh nào

    t1_vec = result.loc[result["tech_id"] == "t1", emb_cols].values[0]
    assert not np.allclose(t1_vec, 0.0)
