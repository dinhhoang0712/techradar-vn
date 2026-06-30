"""
Pipeline trigger endpoint — chạy toàn bộ 5 DVC stages trong background thread.

POST /pipeline/trigger  → khởi động pipeline nếu chưa đang chạy
GET  /pipeline/status   → trạng thái hiện tại (idle|running|success|failed)
"""
from __future__ import annotations

import subprocess
import sys
import threading
from datetime import datetime, timezone
from typing import Literal

from fastapi import APIRouter, Header, HTTPException
from loguru import logger

from conf.config import MODULE_ROOT

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
}

_STAGES = [
    "pipelines.stage_01_extract",
    "pipelines.stage_02_features",
    "pipelines.stage_03_train",
    "pipelines.stage_04_label",
    "pipelines.stage_05_writeback",
]

_PARAMS_PATH = str(MODULE_ROOT / "params.yaml")


def _run_pipeline() -> None:
    t0 = datetime.now(tz=timezone.utc)
    with _LOCK:
        _state["status"]      = "running"
        _state["started_at"]  = t0.isoformat()
        _state["finished_at"] = None
        _state["error"]       = None
        _state["duration_s"]  = None

    logger.info("Pipeline retraining started")

    try:
        for stage in _STAGES:
            with _LOCK:
                _state["current_stage"] = stage
            logger.info("Running stage: {}", stage)

            result = subprocess.run(
                [sys.executable, "-m", stage, "--params", _PARAMS_PATH],
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
