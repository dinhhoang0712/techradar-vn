import pytest
import numpy as np
import pandas as pd
import sys
from pathlib import Path
from unittest.mock import MagicMock

# Tự động thêm đường dẫn module clustering vào PYTHONPATH
ROOT = Path(__file__).resolve().parents[2]
ML_CLUSTERING_ROOT = ROOT / "src" / "ml-clustering"
for p in [str(ML_CLUSTERING_ROOT), str(ML_CLUSTERING_ROOT / "src")]:
    if p not in sys.path:
        sys.path.insert(0, p)

from conf.config import (
    FeatureParams, FastRPParams, Node2VecParams, PageRankParams,
    LouvainParams, ArticleAggParams, ReduceDimParams,
    ClusteringParams, DBSCANGrid, HDBSCANGrid, KMeansGrid, SelectionParams
)

@pytest.fixture
def mock_feature_params():
    return FeatureParams(
        fastrp=FastRPParams(enabled=True),
        node2vec=Node2VecParams(enabled=False),
        pagerank=PageRankParams(enabled=True),
        louvain=LouvainParams(enabled=True, top_k_communities=2),
        article_embedding_aggregation=ArticleAggParams(enabled=True),
        use_company_tfidf=False,
        use_job_tfidf=False,
        scaler="standard",
        reduce_dim=ReduceDimParams(enabled=True, method="pca", n_components=2)
    )

@pytest.fixture
def mock_clustering_params():
    return ClusteringParams(
        algorithm="kmeans",
        dbscan=DBSCANGrid(eps_grid=[0.5], min_samples_grid=[5]),
        hdbscan=HDBSCANGrid(min_cluster_size_grid=[5], min_samples_grid=[1]),
        kmeans=KMeansGrid(n_clusters_grid=[2, 3]),
        selection=SelectionParams(primary_metric="silhouette", require_min_clusters=2)
    )

@pytest.fixture
def dummy_data():
    """Tạo dữ liệu giả lập cho feature pipeline."""
    df_tech = pd.DataFrame({"tech_id": ["t1", "t2", "t3", "t4"]})
    
    gds_features = {
        "pagerank": pd.DataFrame({"tech_id": ["t1", "t2", "t3", "t4"], "pagerank": [0.1, 0.2, 0.3, 0.4]}),
        "fastrp": pd.DataFrame({
            "tech_id": ["t1", "t2", "t3", "t4"],
            "fastrp_0": [1, 2, 3, 4],
            "fastrp_1": [4, 3, 2, 1]
        })
    }
    
    content_emb = pd.DataFrame({
        "tech_id": ["t1", "t2", "t3", "t4"],
        "article_emb_0": [0.1, 0.1, 0.9, 0.9],
        "article_emb_1": [0.1, 0.2, 0.8, 0.9]
    })
    
    graph_stats = pd.DataFrame({
        "tech_id": ["t1", "t2", "t3", "t4"],
        "degree": [10, 20, 30, 40]
    })
    
    return df_tech, gds_features, content_emb, graph_stats
