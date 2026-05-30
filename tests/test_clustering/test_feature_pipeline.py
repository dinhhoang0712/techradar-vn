"""Kiểm tra quy trình xử lý đặc trưng và làm sạch dữ liệu công nghệ."""
import pytest
import pandas as pd
import numpy as np
from src.features.feature_pipeline import build_feature_matrix
from src.features.noise_filter import filter_noise, _SHORT_TECH_WHITELIST
from src.features.acronym_map import expand_tech_name
from src.features.tech_aliases import canonicalize_technology_snapshot
from conf.config import NoiseFilterParams

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
    res = canonicalize_technology_snapshot(df_tech=df_tech, df_edges_mentions=pd.DataFrame(columns=["article_id", "tech_id"]),
        df_edges_company_uses_tech=pd.DataFrame(columns=["company_id", "tech_id"]),
        df_edges_job_requires_tech=df_job, df_edges_tech_related=pd.DataFrame(columns=["tech_id_a", "tech_id_b"]))
    assert len(res.technologies) == 1

def test_acronym_expansion_mapping():
    """Kiểm tra tính chính xác của việc mở rộng các thuật ngữ viết tắt."""
    assert "Secure Shell" in expand_tech_name("SSH")
    assert "Kubernetes" in expand_tech_name("K8s")
