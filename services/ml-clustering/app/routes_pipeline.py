"""
Pipeline trigger endpoint — chạy toàn bộ 5 DVC stages trong background thread.

POST /pipeline/trigger  → khởi động pipeline nếu chưa đang chạy
GET  /pipeline/status   → trạng thái hiện tại (idle|running|success|failed)

Snapshot tag cho mỗi lần trigger qua API được SINH TỰ ĐỘNG (xem
`_generate_snapshot_tag` / `_bump_snapshot_tag`) — khác với workflow thủ công
(`dvc repro`), nơi con người tự sửa `snapshot.tag` trong params.yaml. Lý do:
`take_snapshot()` (stage 1) raise `FileExistsError` nếu tag trùng thư mục cũ,
nên nếu không tự bump tag, lịch chạy tự động hàng tuần (APScheduler trong
data-platform) sẽ luôn fail từ lần thứ 2 trở đi vì tag cố định trong
params.yaml không đổi giữa các lần trigger.
"""
from __future__ import annotations

import re
import subprocess
import sys
import threading
from datetime import datetime, timezone
from pathlib import Path
from typing import Literal
from zoneinfo import ZoneInfo

from fastapi import APIRouter, Header, HTTPException
from loguru import logger

from app.schemas import PipelineRunSummary
from conf.config import MODULE_ROOT, load_params

router = APIRouter(prefix="/pipeline", tags=["pipeline"])

# ── State ──────────────────────────────────────────────────────────────────
_LOCK = threading.Lock()
_state: dict = {
    "status":     "idle",      # idle | running | success | failed
    "started_at": None,
    "finished_at": None,
    "duration_s":  None,
    "error":       None,
    "current_stage": None,
    "snapshot_tag": None,      # tag mới được sinh cho lần trigger này (nếu có)
}

_STAGES = [
    "pipelines.stage_01_extract",
    "pipelines.stage_02_features",
    "pipelines.stage_03_train",
    "pipelines.stage_04_label",
    "pipelines.stage_05_writeback",
]

_PARAMS_PATH_OBJ = MODULE_ROOT / "params.yaml"
_PARAMS_PATH = str(_PARAMS_PATH_OBJ)

# Match dòng `  tag: "..."` trong block `snapshot:` — đây là key `tag:` DUY
# NHẤT trong params.yaml nên regex đơn giản này an toàn.
_TAG_LINE_RE = re.compile(r'^(\s*)tag:\s*"([^"]*)"(.*)$', re.MULTILINE)


def _generate_snapshot_tag() -> str:
    """Sinh tag mới theo thời điểm hiện tại (Asia/Ho_Chi_Minh), phút-chính-xác
    để tránh trùng khi trigger thủ công nhiều lần trong cùng ngày."""
    now = datetime.now(ZoneInfo("Asia/Ho_Chi_Minh"))
    return now.strftime("%Y-%m-%d-%H%M")


def _bump_snapshot_tag(new_tag: str, params_path: Path = _PARAMS_PATH_OBJ) -> str:
    """
    Ghi đè `snapshot.tag` trong params.yaml tại chỗ (giữ nguyên comment/format
    — KHÔNG parse+dump lại YAML vì sẽ mất toàn bộ comment tiếng Việt).
    Trả về tag cũ. Raise RuntimeError nếu không tìm thấy dòng tag.
    """
    text = params_path.read_text(encoding="utf-8")
    match = _TAG_LINE_RE.search(text)
    if match is None:
        raise RuntimeError("Không tìm thấy dòng `tag: \"...\"` trong params.yaml")

    old_tag = match.group(2)
    new_text = _TAG_LINE_RE.sub(
        lambda m: f'{m.group(1)}tag: "{new_tag}"{m.group(3)}', text, count=1
    )
    params_path.write_text(new_text, encoding="utf-8")
    return old_tag


def _run_pipeline() -> None:
    t0 = datetime.now(tz=timezone.utc)
    with _LOCK:
        _state["status"]      = "running"
        _state["started_at"]  = t0.isoformat()
        _state["finished_at"] = None
        _state["error"]       = None
        _state["duration_s"]  = None
        _state["snapshot_tag"] = None

    logger.info("Pipeline retraining started")

    new_tag = _generate_snapshot_tag()
    try:
        old_tag = _bump_snapshot_tag(new_tag)
        logger.info("Snapshot tag bumped for retrain: {} -> {}", old_tag, new_tag)
        with _LOCK:
            _state["snapshot_tag"] = new_tag
    except Exception as exc:
        finished = datetime.now(tz=timezone.utc)
        logger.exception("Không thể bump snapshot tag — huỷ retrain")
        with _LOCK:
            _state["status"]       = "failed"
            _state["finished_at"]  = finished.isoformat()
            _state["error"]        = f"snapshot tag bump failed: {exc}"[:500]
            _state["duration_s"]   = round((finished - t0).total_seconds())
        return

    try:
        for stage in _STAGES:
            with _LOCK:
                _state["current_stage"] = stage
            logger.info("Running stage: {}", stage)

            cmd = [sys.executable, "-m", stage, "--params", _PARAMS_PATH]
            if stage == _STAGES[0]:
                # Lá chắn phòng hờ: tag vừa bump đã unique theo phút, nhưng
                # nếu vẫn trùng (VD 2 lần trigger cùng phút) thì ghi đè thay
                # vì crash toàn bộ retrain.
                cmd.append("--force")

            result = subprocess.run(
                cmd,
                capture_output=True,
                text=True,
                cwd=str(MODULE_ROOT),
            )
            if result.returncode != 0:
                raise RuntimeError(
                    f"Stage {stage} failed (exit {result.returncode}):\n{result.stderr[-1000:]}"
                )

        # Reload store so serving immediately picks up new artifacts
        from app.store import get_store
        get_store.cache_clear()  # type: ignore[attr-defined]
        get_store()

        finished = datetime.now(tz=timezone.utc)
        with _LOCK:
            _state["status"]        = "success"
            _state["finished_at"]   = finished.isoformat()
            _state["current_stage"] = None
            _state["duration_s"]    = round((finished - t0).total_seconds())
        logger.info("Pipeline retraining completed in {}s", _state["duration_s"])

    except Exception as exc:
        finished = datetime.now(tz=timezone.utc)
        with _LOCK:
            _state["status"]        = "failed"
            _state["finished_at"]   = finished.isoformat()
            _state["current_stage"] = None
            _state["error"]         = str(exc)[:500]
            _state["duration_s"]    = round((finished - t0).total_seconds())
        logger.exception("Pipeline retraining FAILED")


# ── Routes ─────────────────────────────────────────────────────────────────

@router.post("/trigger")
def trigger_pipeline(x_internal_auth: str | None = Header(default=None, alias="X-Internal-Auth")):
    """
    Khởi động pipeline retrain (5 stages) trong background thread.
    Trả về ngay lập tức — dùng GET /pipeline/status để theo dõi.
    Bị từ chối nếu pipeline đang chạy.
    """
    import os
    expected = os.getenv("INTERNAL_API_TOKEN", "")
    if expected and x_internal_auth != expected:
        raise HTTPException(status_code=401, detail="Unauthorized")

    with _LOCK:
        if _state["status"] == "running":
            raise HTTPException(
                status_code=409,
                detail=f"Pipeline đang chạy (stage: {_state['current_stage']}). Thử lại sau.",
            )

    thread = threading.Thread(target=_run_pipeline, daemon=True, name="pipeline-retrain")
    thread.start()
    return {"status": "started", "message": "Pipeline retraining started in background"}


@router.get("/status")
def pipeline_status():
    """Trả về trạng thái hiện tại của pipeline retraining."""
    with _LOCK:
        return dict(_state)


@router.get("/runs", response_model=list[PipelineRunSummary])
def pipeline_runs(limit: int = 30):
    """
    Lịch sử các lần train (chỉ run được tag `is_best=true` bởi mlflow_logger.log_best_run,
    tức kết quả cuối cùng của mỗi lần retrain — không phải từng trial của tuner) — dùng để
    theo dõi chất lượng model (silhouette/DBCV/...) qua thời gian và phát hiện model bị suy giảm.
    Đọc trực tiếp từ MLflow tracking store (params.yaml: mlflow.tracking_uri), không qua _state.
    """
    from mlflow.tracking import MlflowClient

    params = load_params()
    client = MlflowClient(tracking_uri=params.mlflow.tracking_uri)

    experiment = client.get_experiment_by_name(params.mlflow.experiment_name)
    if experiment is None:
        return []

    runs = client.search_runs(
        experiment_ids=[experiment.experiment_id],
        filter_string="tags.is_best = 'true'",
        order_by=["start_time DESC"],
        max_results=limit,
    )

    result = []
    for run in runs:
        info = run.info
        data = run.data
        started = (
            datetime.fromtimestamp(info.start_time / 1000, tz=timezone.utc).isoformat()
            if info.start_time else None
        )
        finished = (
            datetime.fromtimestamp(info.end_time / 1000, tz=timezone.utc).isoformat()
            if info.end_time else None
        )
        duration = (
            (info.end_time - info.start_time) / 1000
            if info.start_time and info.end_time else None
        )

        # "best" là 1 nested run — cfg.snapshot.tag chỉ được log 1 lần trên parent run
        # (xem stage_03_train.py: parent_run(f"train_{tag}", tags={"snapshot": tag, ...})),
        # nên phải đi ngược lên parent để lấy lại tag của lần retrain này.
        snapshot_tag = None
        parent_run_id = data.tags.get("mlflow.parentRunId")
        if parent_run_id:
            try:
                snapshot_tag = client.get_run(parent_run_id).data.tags.get("snapshot")
            except Exception:
                logger.warning("Could not resolve parent run {} for snapshot tag", parent_run_id)

        result.append(PipelineRunSummary(
            run_id=info.run_id,
            snapshot_tag=snapshot_tag,
            status=info.status,
            started_at=started,
            finished_at=finished,
            duration_s=duration,
            algorithm=data.params.get("algorithm"),
            metrics=dict(data.metrics),
        ))
    return result
