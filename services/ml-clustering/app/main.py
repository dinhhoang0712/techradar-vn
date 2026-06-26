"""
ml-clustering API — FastAPI app.

Endpoints:
  GET  /health                   → health check + snapshot info
  GET  /clusters                 → danh sách tất cả cluster + label
  GET  /clusters/{cluster_id}    → chi tiết 1 cluster + members
  GET  /tech/{tech_name}/cluster → tech này thuộc cluster nào
  POST /predict/batch            → batch lookup nhiều tech names

Chạy:
  cd src/ml-clustering
  uvicorn app.main:app --reload --port 8001
"""
from __future__ import annotations

import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI, HTTPException, Query

from app.observability import RequestContextMiddleware, configure_logging
from app.schemas import (
    BatchPredictRequest,
    BatchPredictResponse,
    ClusterDetail,
    ClusterSummary,
    TechClusterResult,
)
from app.store import get_store

# Configure JSON logging before anything else emits logs.
configure_logging()
logger = logging.getLogger("ml-clustering")


# ---------------------------------------------------------------------------
# Lifespan: warm up store (load artifacts) on startup
# ---------------------------------------------------------------------------

@asynccontextmanager
async def lifespan(app: FastAPI):
    store = get_store()  # trigger load + cache
    logger.info(
        "Store ready: snapshot_tag=%s source=%s n_techs=%d n_clusters=%d",
        store.tag, store.source, len(store.tech_to_cluster), len(store.cluster_labels),
    )
    yield


app = FastAPI(
    title="TechPulse ML Clustering API",
    description="Serve kết quả phân cụm công nghệ từ pipeline HDBSCAN + GPT-4o labeling.",
    version="1.0.0",
    lifespan=lifespan,
)

# Trace-id binding + access logging (outermost middleware → every request gets a trace id).
app.add_middleware(RequestContextMiddleware)


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def _build_tech_result(name: str, store) -> TechClusterResult:
    tech_id, cluster_id = store.lookup_tech(name)
    if tech_id is None:
        return TechClusterResult(
            tech_name=name,
            tech_id=None,
            cluster_id=None,
            label=None,
            label_en=None,
            domain=None,
            found=False,
        )

    label_info = store.get_cluster_label(cluster_id) if cluster_id is not None and cluster_id != -1 else None
    return TechClusterResult(
        tech_name=name,
        tech_id=tech_id,
        cluster_id=cluster_id,
        label=label_info.get("label") if label_info else None,
        label_en=label_info.get("label_en") if label_info else None,
        domain=label_info.get("domain") if label_info else None,
        found=True,
    )


# ---------------------------------------------------------------------------
# Routes
# ---------------------------------------------------------------------------

@app.get("/health")
def health():
    store = get_store()
    n_clustered = sum(1 for cid in store.tech_to_cluster.values() if cid != -1)
    n_noise = sum(1 for cid in store.tech_to_cluster.values() if cid == -1)
    return {
        "status": "ok",
        "snapshot_tag": store.tag,
        "requested_snapshot_tag": store.requested_tag,
        "artifact_source": store.source,
        "n_techs_total": len(store.tech_to_cluster),
        "n_clustered": n_clustered,
        "n_noise": n_noise,
        "n_clusters": len(store.cluster_labels),
    }


@app.get("/clusters", response_model=list[ClusterSummary])
def list_clusters(is_coherent: bool | None = Query(default=None)):
    """Danh sách tất cả cluster (bỏ noise cluster -1)."""
    store = get_store()
    result = []
    for cid, info in sorted(store.cluster_labels.items()):
        if cid == -1:
            continue
        if is_coherent is not None and info.get("is_coherent", True) != is_coherent:
            continue
        members = store.cluster_to_techs.get(cid, [])
        result.append(ClusterSummary(
            cluster_id=cid,
            label=info.get("label", ""),
            label_en=info.get("label_en", ""),
            domain=info.get("domain", "Other"),
            confidence=info.get("confidence", 0.0),
            is_coherent=info.get("is_coherent", True),
            n_members=len(members),
        ))
    return result


@app.get("/clusters/{cluster_id}", response_model=ClusterDetail)
def get_cluster(cluster_id: int):
    """Chi tiết 1 cluster kèm danh sách tech members."""
    store = get_store()
    info = store.get_cluster_label(cluster_id)
    if info is None:
        raise HTTPException(status_code=404, detail=f"Cluster {cluster_id} không tồn tại.")

    members = store.cluster_to_techs.get(cluster_id, [])
    return ClusterDetail(
        cluster_id=cluster_id,
        label=info.get("label", ""),
        label_en=info.get("label_en", ""),
        domain=info.get("domain", "Other"),
        confidence=info.get("confidence", 0.0),
        is_coherent=info.get("is_coherent", True),
        description=info.get("description", ""),
        coherence_reason=info.get("coherence_reason"),
        outliers=info.get("outliers", []),
        n_members=len(members),
        members=sorted(members),
    )


@app.get("/tech/{tech_name}/cluster", response_model=TechClusterResult)
def get_tech_cluster(tech_name: str):
    """Tra cứu cluster của 1 công nghệ theo tên."""
    store = get_store()
    result = _build_tech_result(tech_name, store)
    if not result.found:
        raise HTTPException(
            status_code=404,
            detail=f"'{tech_name}' không có trong snapshot (tag={store.tag}). "
                   "Chạy lại pipeline khi DB được update.",
        )
    return result


@app.post("/predict/batch", response_model=BatchPredictResponse)
def predict_batch(req: BatchPredictRequest):
    """
    Batch lookup cluster cho danh sách tech names.

    - Tìm trong snapshot hiện tại (best_labels.parquet).
    - `found=false` nếu tech chưa có trong DB / chưa pass noise filter.
    - Khi DB update → chạy lại pipeline → gọi lại endpoint này.
    """
    store = get_store()
    results = [_build_tech_result(name, store) for name in req.tech_names]
    n_found = sum(1 for r in results if r.found)
    return BatchPredictResponse(
        results=results,
        n_found=n_found,
        n_not_found=len(results) - n_found,
        snapshot_tag=store.tag,
    )
