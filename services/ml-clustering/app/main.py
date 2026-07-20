"""
ml-clustering API — FastAPI app.

Endpoints:
  GET  /health                   → health check + snapshot info
  GET  /clusters                 → danh sách tất cả cluster + label
  GET  /clusters/{cluster_id}    → chi tiết 1 cluster + members
  PUT  /clusters/{cluster_id}/label → admin ghi đè nhãn AI-generated (internal-auth)
  GET  /tech/{tech_name}/cluster → tech này thuộc cluster nào
  POST /predict/batch            → batch lookup nhiều tech names

Chạy:
  cd src/ml-clustering
  uvicorn app.main:app --reload --port 8001
"""
from __future__ import annotations

import logging
import os
from contextlib import asynccontextmanager

from fastapi import FastAPI, Header, HTTPException, Query
from fastapi.responses import JSONResponse

from app.observability import RequestContextMiddleware, configure_logging
from app.routes_pipeline import router as pipeline_router
from app.schemas import (
    BatchPredictRequest,
    BatchPredictResponse,
    ClusterDetail,
    ClusterLabelOverrideRequest,
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
app.include_router(pipeline_router)


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def _build_tech_result(name: str, store) -> TechClusterResult:
    tech_id, cluster_id = store.lookup_tech(name)
    if tech_id is None:
        provisional = store.find_nearest_known_tech(name)
        if provisional is None:
            return TechClusterResult(
                tech_name=name,
                tech_id=None,
                cluster_id=None,
                label=None,
                label_en=None,
                domain=None,
                found=False,
            )
        matched_name, matched_cluster_id, score = provisional
        label_info = store.get_cluster_label(matched_cluster_id)
        return TechClusterResult(
            tech_name=name,
            tech_id=None,
            cluster_id=matched_cluster_id,
            label=label_info.get("label") if label_info else None,
            label_en=label_info.get("label_en") if label_info else None,
            domain=label_info.get("domain") if label_info else None,
            found=False,
            provisional=True,
            matched_via=matched_name,
            match_score=score,
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
        membership_probability=store.tech_membership_probability.get(tech_id),
        outlier_score=store.tech_outlier_score.get(tech_id),
        near_clusters=store.get_near_clusters(tech_id),
    )


# ---------------------------------------------------------------------------
# Routes
# ---------------------------------------------------------------------------

@app.get("/health")
def health():
    """
    Trả 503 khi artifact chưa load được (`store.data_available=False`) — trước đây luôn
    trả 200 kể cả khi store rỗng (vd artifact chưa từng được sinh, hoặc load lỗi), khiến
    Docker healthcheck báo "healthy" cho 1 service thực chất không phục vụ được gì.
    """
    store = get_store()
    n_clustered = sum(1 for cid in store.tech_to_cluster.values() if cid != -1)
    n_noise = sum(1 for cid in store.tech_to_cluster.values() if cid == -1)
    payload = {
        "status": "ok" if store.data_available else "degraded",
        "data_available": store.data_available,
        "snapshot_tag": store.tag,
        "requested_snapshot_tag": store.requested_tag,
        "artifact_source": store.source,
        "n_techs_total": len(store.tech_to_cluster),
        "n_clustered": n_clustered,
        "n_noise": n_noise,
        "n_clusters": len(store.cluster_labels),
    }
    if not store.data_available:
        return JSONResponse(status_code=503, content=payload)
    return payload


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
            overridden=info.get("overridden", False),
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
        overridden=info.get("overridden", False),
        overridden_by=info.get("overridden_by"),
        overridden_at=info.get("overridden_at"),
    )


@app.put("/clusters/{cluster_id}/label", response_model=ClusterDetail)
def update_cluster_label(
    cluster_id: int,
    body: ClusterLabelOverrideRequest,
    x_internal_auth: str | None = Header(default=None, alias="X-Internal-Auth"),
    x_actor: str | None = Header(default=None, alias="X-Actor"),
):
    """
    Admin ghi đè nhãn AI-generated (label/label_en/description/domain) cho 1 cluster —
    dùng khi LLM labeler gán sai/mơ hồ. `X-Actor` (tuỳ chọn) là id người thực hiện, phục
    vụ hiển thị "đã sửa bởi ai" — không dùng để xác thực.
    """
    expected = os.getenv("INTERNAL_API_TOKEN", "")
    if expected and x_internal_auth != expected:
        raise HTTPException(status_code=401, detail="Unauthorized")

    store = get_store()
    try:
        info = store.save_cluster_override(
            cluster_id,
            label=body.label,
            label_en=body.label_en,
            description=body.description,
            domain=body.domain,
            actor=x_actor,
        )
    except KeyError as exc:
        raise HTTPException(status_code=404, detail=str(exc)) from exc
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc

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
        overridden=info.get("overridden", False),
        overridden_by=info.get("overridden_by"),
        overridden_at=info.get("overridden_at"),
    )


@app.get("/tech/{tech_name}/cluster", response_model=TechClusterResult)
def get_tech_cluster(tech_name: str):
    """
    Tra cứu cluster của 1 công nghệ theo tên. Tech chưa có trong snapshot nhưng
    khớp đủ giống 1 tech đã biết (`provisional=True`, xem `find_nearest_known_tech`)
    vẫn trả 200 kèm cluster tạm — chỉ 404 khi thật sự không suy ra được gì.
    """
    store = get_store()
    result = _build_tech_result(tech_name, store)
    if not result.found and not result.provisional:
        raise HTTPException(
            status_code=404,
            detail=f"'{tech_name}' không có trong snapshot (tag={store.tag}), và không khớp đủ "
                   "giống tech nào đã biết để gán tạm. Chạy lại pipeline khi DB được update.",
        )
    return result


@app.post("/predict/batch", response_model=BatchPredictResponse)
def predict_batch(req: BatchPredictRequest):
    """
    Batch lookup cluster cho danh sách tech names.

    - Tìm trong snapshot hiện tại (best_labels.parquet).
    - `found=false` nếu tech chưa có trong DB / chưa pass noise filter — nhưng nếu khớp
      đủ giống 1 tech đã biết, vẫn trả cluster tạm (`provisional=true`, xem `n_provisional`).
    - Khi DB update → chạy lại pipeline → gọi lại endpoint này để có kết quả HDBSCAN thật.
    """
    store = get_store()
    results = [_build_tech_result(name, store) for name in req.tech_names]
    n_found = sum(1 for r in results if r.found)
    n_provisional = sum(1 for r in results if r.provisional)
    n_not_found = sum(1 for r in results if not r.found and not r.provisional)
    return BatchPredictResponse(
        results=results,
        n_found=n_found,
        n_provisional=n_provisional,
        n_not_found=n_not_found,
        snapshot_tag=store.tag,
    )
