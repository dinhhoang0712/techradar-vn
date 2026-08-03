"""
Pipeline trigger endpoint — chạy 6 stage (extract, features, train, label, publish,
writeback) trong background thread.

POST /pipeline/trigger  → khởi động pipeline nếu chưa đang chạy
GET  /pipeline/status   → trạng thái hiện tại (idle|running|success|failed)

Snapshot tag cho mỗi lần trigger qua API được SINH TỰ ĐỘNG (xem
`_generate_snapshot_tag` / `_bump_snapshot_tag`) — khác với workflow thủ công
(`dvc repro`), nơi con người tự sửa `snapshot.tag` trong params.yaml. Lý do:
`take_snapshot()` (stage 1) raise `FileExistsError` nếu tag trùng thư mục cũ,
nên nếu không tự bump tag, lịch chạy tự động hàng tuần (APScheduler trong
data-platform) sẽ luôn fail từ lần thứ 2 trở đi vì tag cố định trong
params.yaml không đổi giữa các lần trigger.

Sau TRAIN, pipeline kiểm tra model mới có được promote lên champion không (xem
is_run_promoted_to_champion) — nếu không, label/publish/writeback bị bỏ qua và
params.yaml được trả về tag cũ (xem _run_pipeline), tránh tốn LLM cost/ghi Neo4j/
phá cache serving cho 1 model tệ hơn cái đang chạy. `force=true` bỏ qua gate này.
"""

from __future__ import annotations

import json
import os
import re
import subprocess
import sys
import threading
from datetime import UTC, datetime
from pathlib import Path
from zoneinfo import ZoneInfo

import redis
from conf.config import MODULE_ROOT, load_params
from fastapi import APIRouter, Header, HTTPException
from loguru import logger

from app.schemas import PipelineRunSummary

router = APIRouter(prefix="/pipeline", tags=["pipeline"])

# Admin notification on pipeline finish (see JobCompletionNotifier on the Java side, which
# subscribes to this exact channel). redis-py connects lazily on first command, so building
# this unconditionally is safe even if Redis isn't reachable yet when the container starts.
REDIS_URL = os.getenv("REDIS_URL", "redis://localhost:6379")
COMPLETED_CHANNEL = "clustering:completed"
_redis_client = redis.Redis.from_url(REDIS_URL, socket_connect_timeout=5, socket_timeout=5)

# ── State ──────────────────────────────────────────────────────────────────
_LOCK = threading.Lock()
_state: dict = {
    "status": "idle",  # idle | running | success | failed
    "started_at": None,
    "finished_at": None,
    "duration_s": None,
    "error": None,
    "current_stage": None,
    "snapshot_tag": None,  # tag mới được sinh cho lần trigger này (nếu có)
    # True nếu model mới thực sự được label/publish/writeback (promoted lên champion hoặc
    # force=true); False nếu train thành công nhưng KHÔNG tốt hơn champion hiện tại nên bị
    # bỏ qua bước deploy (xem _run_pipeline) — vẫn "status": "success" vì bản thân việc train
    # không lỗi, chỉ là không có gì mới để serve.
    "deployed": None,
    "note": None,
}

# Chia 2 nhóm: _TRAIN_STAGES luôn chạy; _DEPLOY_STAGES (label/publish/writeback) chỉ chạy
# nếu model mới được promote lên champion (xem is_run_promoted_to_champion) — tránh tốn
# LLM cost (label) + ghi Neo4j (writeback) + phá cache serving (publish) cho 1 lần retrain
# ra model TỆ HƠN champion hiện tại. publish đứng TRƯỚC writeback (không phải sau như cũ):
# nếu publish lỗi (MinIO down, thiếu artifact...), Neo4j graph không bị đụng vào — tránh 2 hệ
# thống (serving API vs graph) lệch nhau khi chỉ 1 trong 2 side-effect thành công.
_TRAIN_STAGES = [
    "pipelines.stage_01_extract",
    "pipelines.stage_02_features",
    "pipelines.stage_03_train",
]
_DEPLOY_STAGES = [
    "pipelines.stage_04_label",
    "pipelines.stage_06_publish",
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


def _resolve_best_run_id(params) -> str:
    """
    stage_05_writeback cần `--run-id` (run MLflow "best" mà stage_03_train vừa log qua
    mlflow_logger.log_best_run) để tải near_clusters.json. `_LOCK` đảm bảo chỉ 1 lần retrain
    chạy tại một thời điểm, nên run `is_best=true` mới nhất luôn là của snapshot vừa train xong —
    không cần lọc thêm theo tag.
    """
    from mlflow.tracking import MlflowClient

    client = MlflowClient(tracking_uri=params.mlflow.tracking_uri)
    experiment = client.get_experiment_by_name(params.mlflow.experiment_name)
    if experiment is None:
        raise RuntimeError(f"MLflow experiment '{params.mlflow.experiment_name}' không tồn tại")

    runs = client.search_runs(
        experiment_ids=[experiment.experiment_id],
        filter_string="tags.is_best = 'true'",
        order_by=["start_time DESC"],
        max_results=1,
    )
    if not runs:
        raise RuntimeError("Không tìm thấy run 'is_best=true' nào — stage_03_train có thể chưa log best run")
    return runs[0].info.run_id


def _bump_snapshot_tag(new_tag: str, params_path: Path = _PARAMS_PATH_OBJ) -> str:
    """
    Ghi đè `snapshot.tag` trong params.yaml tại chỗ (giữ nguyên comment/format
    — KHÔNG parse+dump lại YAML vì sẽ mất toàn bộ comment tiếng Việt).
    Trả về tag cũ. Raise RuntimeError nếu không tìm thấy dòng tag.
    """
    text = params_path.read_text(encoding="utf-8")
    match = _TAG_LINE_RE.search(text)
    if match is None:
        raise RuntimeError('Không tìm thấy dòng `tag: "..."` trong params.yaml')

    old_tag = match.group(2)
    new_text = _TAG_LINE_RE.sub(lambda m: f'{m.group(1)}tag: "{new_tag}"{m.group(3)}', text, count=1)
    params_path.write_text(new_text, encoding="utf-8")
    return old_tag


def _publish_completion() -> None:
    """Admin notification is a nice-to-have — never block/fail the retrain itself over it
    (mirrors services/crawler/run_all.py's identical fire-and-forget pattern)."""
    with _LOCK:
        payload = {
            "status": _state["status"],
            "duration_s": _state["duration_s"],
            "snapshot_tag": _state["snapshot_tag"],
            "error": _state["error"],
        }
    try:
        _redis_client.publish(COMPLETED_CHANNEL, json.dumps(payload))
    except redis.exceptions.RedisError as e:
        logger.warning("Could not publish clustering completion to Redis: {}", e)


def _run_stage(stage: str, *, extra_args: list[str] | None = None) -> None:
    cmd = [sys.executable, "-m", stage, "--params", _PARAMS_PATH, *(extra_args or [])]
    result = subprocess.run(cmd, capture_output=True, text=True, cwd=str(MODULE_ROOT))
    if result.returncode != 0:
        raise RuntimeError(f"Stage {stage} failed (exit {result.returncode}):\n{result.stderr[-1000:]}")


def _run_pipeline(force: bool = False) -> None:
    t0 = datetime.now(tz=UTC)
    with _LOCK:
        _state["status"] = "running"
        _state["started_at"] = t0.isoformat()
        _state["finished_at"] = None
        _state["error"] = None
        _state["duration_s"] = None
        _state["snapshot_tag"] = None
        _state["deployed"] = None
        _state["note"] = None

    logger.info("Pipeline retraining started (force={})", force)

    new_tag = _generate_snapshot_tag()
    try:
        old_tag = _bump_snapshot_tag(new_tag)
        logger.info("Snapshot tag bumped for retrain: {} -> {}", old_tag, new_tag)
        with _LOCK:
            _state["snapshot_tag"] = new_tag
    except Exception as exc:
        finished = datetime.now(tz=UTC)
        logger.exception("Không thể bump snapshot tag — huỷ retrain")
        with _LOCK:
            _state["status"] = "failed"
            _state["finished_at"] = finished.isoformat()
            _state["error"] = f"snapshot tag bump failed: {exc}"[:500]
            _state["duration_s"] = round((finished - t0).total_seconds())
        _publish_completion()
        return

    try:
        for stage in _TRAIN_STAGES:
            with _LOCK:
                _state["current_stage"] = stage
            logger.info("Running stage: {}", stage)
            # Lá chắn phòng hờ: tag vừa bump đã unique theo phút, nhưng nếu vẫn trùng (VD 2 lần
            # trigger cùng phút) thì ghi đè thay vì crash toàn bộ retrain.
            extra = ["--force"] if stage == _TRAIN_STAGES[0] else None
            _run_stage(stage, extra_args=extra)

        from src.tracking.mlflow_logger import is_run_promoted_to_champion

        params_obj = load_params()
        best_run_id = _resolve_best_run_id(params_obj)
        promoted = force or is_run_promoted_to_champion(best_run_id, params_obj.mlflow.tracking_uri)

        if not promoted:
            # Model mới KHÔNG tốt hơn champion hiện tại (xem register_best_model ở
            # stage_03_train) — bỏ qua label/publish/writeback để không tốn LLM cost, không
            # ghi Neo4j, không phá cache serving cho 1 model tệ hơn cái đang chạy. params.yaml
            # đã bị bump sang new_tag ở trên — PHẢI trả lại old_tag, nếu không lần restart/
            # reset_store() kế tiếp sẽ cố load new_tag (thiếu label/writeback → data_available=false).
            _bump_snapshot_tag(old_tag)
            logger.info("Model không được promote — trả params.yaml về tag cũ: {}", old_tag)
            note = f"Model mới (tag={new_tag}) không tốt hơn champion hiện tại — bỏ qua deploy, vẫn phục vụ {old_tag}."
            finished = datetime.now(tz=UTC)
            with _LOCK:
                _state["status"] = "success"
                _state["finished_at"] = finished.isoformat()
                _state["current_stage"] = None
                _state["duration_s"] = round((finished - t0).total_seconds())
                _state["deployed"] = False
                _state["note"] = note
            logger.info(note)
            _publish_completion()
            return

        for stage in _DEPLOY_STAGES:
            with _LOCK:
                _state["current_stage"] = stage
            logger.info("Running stage: {}", stage)

            if stage == "pipelines.stage_06_publish":
                _run_stage(stage, extra_args=["--run-id", best_run_id])
            elif stage == "pipelines.stage_05_writeback":
                # CLI mặc định --dry-run (an toàn cho chạy tay), nhưng qua HTTP trigger thì phải
                # ghi thật — thiếu cờ này khiến stage luôn no-op (chỉ in preview, exit 0) dù
                # writeback.enabled=true, nên Neo4j không bao giờ có :Cluster/:BELONGS_TO thật.
                _run_stage(stage, extra_args=["--run-id", best_run_id, "--no-dry-run"])
            else:
                _run_stage(stage)

        # Reload store so serving immediately picks up new artifacts (no-op nếu MinIO không
        # cấu hình — reset_store() đọc lại từ local disk, nơi stage_04/05 đã ghi trực tiếp).
        from app.store import get_store, reset_store

        reset_store()
        get_store()

        finished = datetime.now(tz=UTC)
        with _LOCK:
            _state["status"] = "success"
            _state["finished_at"] = finished.isoformat()
            _state["current_stage"] = None
            _state["duration_s"] = round((finished - t0).total_seconds())
            _state["deployed"] = True
        logger.info("Pipeline retraining completed in {}s", _state["duration_s"])
        _publish_completion()

    except Exception as exc:
        finished = datetime.now(tz=UTC)
        with _LOCK:
            _state["status"] = "failed"
            _state["finished_at"] = finished.isoformat()
            _state["current_stage"] = None
            _state["error"] = str(exc)[:500]
            _state["duration_s"] = round((finished - t0).total_seconds())
            _state["deployed"] = False
        logger.exception("Pipeline retraining FAILED")
        _publish_completion()


# ── Routes ─────────────────────────────────────────────────────────────────


@router.post("/trigger")
def trigger_pipeline(
    force: bool = False,
    x_internal_auth: str | None = Header(default=None, alias="X-Internal-Auth"),
):
    """
    Khởi động pipeline retrain (6 stages) trong background thread.
    Trả về ngay lập tức — dùng GET /pipeline/status để theo dõi.
    Bị từ chối nếu pipeline đang chạy.

    `force=true` bỏ qua champion gate — label/publish/writeback dù model mới không tốt hơn
    champion hiện tại (vd muốn deploy để so sánh qualitative dù metric thấp hơn 1 chút).
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

    thread = threading.Thread(target=_run_pipeline, kwargs={"force": force}, daemon=True, name="pipeline-retrain")
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
        started = datetime.fromtimestamp(info.start_time / 1000, tz=UTC).isoformat() if info.start_time else None
        finished = datetime.fromtimestamp(info.end_time / 1000, tz=UTC).isoformat() if info.end_time else None
        duration = (info.end_time - info.start_time) / 1000 if info.start_time and info.end_time else None

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

        result.append(
            PipelineRunSummary(
                run_id=info.run_id,
                snapshot_tag=snapshot_tag,
                status=info.status,
                started_at=started,
                finished_at=finished,
                duration_s=duration,
                algorithm=data.params.get("algorithm"),
                metrics=dict(data.metrics),
            )
        )
    return result
