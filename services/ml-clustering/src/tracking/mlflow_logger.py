"""
Wrapper MLflow — chỉ phơi 1 vài hàm cao cấp để Stage train/label gọi.

Triết lý:
  - Mỗi lần chạy `pipelines.stage_03_train` = 1 parent run.
  - Mỗi trial trong grid search = 1 nested run dưới parent.
  - Trial tốt nhất được register vào Model Registry với name = `params.mlflow.registry_model_name`.
  - Tag mọi run với `dataset_snapshot=<tag>` + `git_commit=<hash>` để truy nguyên.
"""

from __future__ import annotations

import json
import logging
import pickle
import subprocess
import tempfile
from contextlib import contextmanager
from pathlib import Path
from typing import Any, Iterator

import mlflow
import mlflow.sklearn
import numpy as np

from conf.config import MLflowParams
from src.clustering.tuner import TrialResult

logger = logging.getLogger(__name__)

_MAX_PARAM_LEN = 500


def _serialize_param_value(value: Any) -> str:
    if isinstance(value, (dict, list)):
        text = json.dumps(value, ensure_ascii=False)
    else:
        text = str(value)

    if len(text) > _MAX_PARAM_LEN:
        return text[: _MAX_PARAM_LEN - 3] + "..."
    return text


def _flatten_params(data: Any, prefix: str) -> dict[str, str]:
    flat: dict[str, str] = {}
    if isinstance(data, dict):
        for key, value in data.items():
            next_prefix = f"{prefix}.{key}" if prefix else str(key)
            flat.update(_flatten_params(value, next_prefix))
        return flat

    flat[prefix] = _serialize_param_value(data)
    return flat


def _get_git_commit() -> str | None:
    try:
        return subprocess.check_output(
            ["git", "rev-parse", "--short", "HEAD"], text=True
        ).strip()
    except Exception:
        return None


def init_mlflow(params: MLflowParams) -> None:
    """Khởi tạo MLflow tracking URI + experiment. Idempotent."""
    mlflow.set_tracking_uri(params.tracking_uri)
    mlflow.set_experiment(params.experiment_name)
    mlflow.sklearn.autolog(log_models=False, silent=True)
    logger.info("MLflow init: uri=%s experiment=%s", params.tracking_uri, params.experiment_name)


def log_params_from_yaml(params_obj: Any, prefix: str = "cfg") -> None:
    """Log toàn bộ config trong params.yaml theo dạng flattened keys."""
    raw = params_obj.model_dump() if hasattr(params_obj, "model_dump") else params_obj
    flat = _flatten_params(raw, prefix=prefix)
    if flat:
        mlflow.log_params(flat)


@contextmanager
def parent_run(
    run_name: str,
    tags: dict[str, str] | None = None,
) -> Iterator[mlflow.ActiveRun]:
    """Context manager mở 1 parent MLflow run, tự đóng khi exit."""
    all_tags = dict(tags or {})
    git = _get_git_commit()
    if git:
        all_tags["git_commit"] = git

    with mlflow.start_run(run_name=run_name, tags=all_tags) as run:
        logger.info("Parent run started: %s (id=%s)", run_name, run.info.run_id)
        yield run


def log_trial(trial: TrialResult, parent_run_id: str | None = None) -> str:
    """Log 1 trial vào nested MLflow run. Trả về run_id của trial."""
    run_name = f"trial_{trial.algorithm}_" + "_".join(
        f"{k}{v}" for k, v in list(trial.params.items())[:2]
    )
    with mlflow.start_run(run_name=run_name, nested=True) as run:
        # Params
        mlflow.log_params({"algorithm": trial.algorithm, **trial.params})

        # Metrics — bỏ qua None/NaN
        metrics: dict[str, float] = {
            "n_clusters":         float(trial.n_clusters),
            "n_noise":            float(trial.n_noise),
            "noise_ratio":        trial.noise_ratio,
            "wall_seconds":       trial.wall_seconds,
            "passed_constraints": 1.0 if trial.passed_constraints else 0.0,
        }
        for key in ("silhouette", "davies_bouldin", "calinski_harabasz"):
            val = getattr(trial, key)
            if val is not None and not (isinstance(val, float) and np.isnan(val)):
                metrics[key] = float(val)

        mlflow.log_metrics(metrics)

        if trial.failure_reason:
            mlflow.set_tag("failure_reason", trial.failure_reason[:500])

        return run.info.run_id


def log_feature_meta(meta_path: str | Path) -> None:
    """Upload feature_meta.json làm artifact."""
    mlflow.log_artifact(str(meta_path), artifact_path="input")


def log_best_run(
    best_trial: TrialResult,
    model: Any,
    X: np.ndarray,
    tech_ids: list[str],
    extra_artifacts: dict[str, str | Path] | None = None,
) -> str:
    """
    Mở nested run "best" → log params, metrics, model pickle, labels JSON.
    Trả về run_id để stage 4 + writeback dùng.
    """
    with mlflow.start_run(run_name="best", nested=True) as run:
        mlflow.set_tag("is_best", "true")
        mlflow.log_params({"algorithm": best_trial.algorithm, **best_trial.params})

        metrics: dict[str, float] = {
            "n_clusters":   float(best_trial.n_clusters),
            "noise_ratio":  best_trial.noise_ratio,
            "wall_seconds": best_trial.wall_seconds,
        }
        for key in ("silhouette", "davies_bouldin", "calinski_harabasz"):
            val = getattr(best_trial, key)
            if val is not None and not (isinstance(val, float) and np.isnan(val)):
                metrics[key] = float(val)
        mlflow.log_metrics(metrics)

        # Log labels + tech_ids
        mlflow.log_dict(
            {"tech_ids": tech_ids, "labels": best_trial.labels.tolist()},
            "best_labels.json",
        )

        # Log model — pickle vì HDBSCAN không hỗ trợ mlflow.sklearn.log_model
        with tempfile.NamedTemporaryFile(suffix=".pkl", delete=False) as f:
            pickle.dump(model, f)
            tmp_path = f.name
        mlflow.log_artifact(tmp_path, artifact_path="model")
        Path(tmp_path).unlink(missing_ok=True)

        # Extra artifacts (ví dụ: near_clusters.json)
        for name, path in (extra_artifacts or {}).items():
            mlflow.log_artifact(str(path), artifact_path=name)

        run_id = run.info.run_id
        logger.info("Best run logged: run_id=%s", run_id)
        return run_id


def register_best_model(run_id: str, registry_name: str) -> int:
    """
    Đăng ký model vào MLflow Model Registry, set alias 'champion'.
    Trả về version number.
    """
    model_uri = f"runs:/{run_id}/model"
    result = mlflow.register_model(model_uri, registry_name)
    version = int(result.version)

    client = mlflow.tracking.MlflowClient()
    client.set_registered_model_alias(registry_name, "champion", version)
    logger.info(
        "Model registered: %s v%d (alias=champion)", registry_name, version
    )
    return version


def write_metrics_file(
    metrics: dict[str, float | int],
    out_path: str | Path,
) -> None:
    """Ghi best_metrics.json để DVC track (dvc metrics show)."""
    out = Path(out_path)
    out.parent.mkdir(parents=True, exist_ok=True)
    # Chỉ giữ primitive values — loại NaN/None
    clean = {
        k: v for k, v in metrics.items()
        if isinstance(v, (int, float, str, bool))
        and not (isinstance(v, float) and np.isnan(v))
    }
    out.write_text(json.dumps(clean, indent=2), encoding="utf-8")
    logger.info("Metrics file: %s", out)
