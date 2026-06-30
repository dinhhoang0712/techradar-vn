"""
AppStore — load và cache artifacts từ disk hoặc S3.

Artifacts:
  - best_labels.parquet  : tech_id → cluster_id
  - cluster_labels.json  : cluster_id → label metadata
  - technologies.parquet : tech_id → name (từ snapshot)

Gọi `get_store()` ở bất kỳ đâu. Nếu MLCLUSTER_SNAPSHOT_TAG=latest,
store sẽ đọc manifest trên S3 và tự reload theo TTL khi tag đổi.
"""
from __future__ import annotations

import json
import logging
import os
from pathlib import Path
from threading import RLock
from time import monotonic

import boto3
from botocore.config import Config
from botocore.exceptions import BotoCoreError, ClientError
import pandas as pd

from conf.config import DATA_DIR, load_params


logger = logging.getLogger(__name__)


def _get_s3_settings() -> dict | None:
    bucket = os.getenv("MLCLUSTER_S3_BUCKET")
    if not bucket:
        return None

    prefix = os.getenv("MLCLUSTER_S3_PREFIX", "").strip("/")
    cache_dir = os.getenv("MLCLUSTER_S3_CACHE_DIR", "")
    endpoint_url = os.getenv("MLCLUSTER_S3_ENDPOINT_URL")
    region = os.getenv("MLCLUSTER_S3_REGION")
    access_key = os.getenv("MLCLUSTER_S3_ACCESS_KEY_ID")
    secret_key = os.getenv("MLCLUSTER_S3_SECRET_ACCESS_KEY")
    addressing_style = os.getenv("MLCLUSTER_S3_ADDRESSING_STYLE")

    return {
        "bucket": bucket,
        "prefix": prefix,
        "cache_dir": cache_dir,
        "endpoint_url": endpoint_url,
        "region": region,
        "access_key": access_key,
        "secret_key": secret_key,
        "addressing_style": addressing_style,
    }


def _make_s3_client(settings: dict) -> boto3.client:
    config = None
    if settings.get("addressing_style"):
        config = Config(s3={"addressing_style": settings["addressing_style"]})

    return boto3.client(
        "s3",
        endpoint_url=settings.get("endpoint_url") or None,
        region_name=settings.get("region") or None,
        aws_access_key_id=settings.get("access_key") or None,
        aws_secret_access_key=settings.get("secret_key") or None,
        config=config,
    )


def _s3_key(prefix: str, rel_path: str) -> str:
    if prefix:
        return f"{prefix}/{rel_path}"
    return rel_path


def _read_s3_json(settings: dict, rel_path: str) -> dict:
    key = _s3_key(settings.get("prefix", ""), rel_path.strip("/"))
    client = _make_s3_client(settings)
    try:
        obj = client.get_object(Bucket=settings["bucket"], Key=key)
        body = obj["Body"].read()
    except (BotoCoreError, ClientError) as exc:
        raise FileNotFoundError(f"S3 manifest read failed: s3://{settings['bucket']}/{key}") from exc

    try:
        data = json.loads(body.decode("utf-8"))
    except json.JSONDecodeError as exc:
        raise ValueError(f"S3 manifest is not valid JSON: s3://{settings['bucket']}/{key}") from exc

    if not isinstance(data, dict):
        raise ValueError(f"S3 manifest must be a JSON object: s3://{settings['bucket']}/{key}")
    return data


def _local_path(cache_dir: str, rel_path: str) -> Path:
    base = Path(cache_dir) if cache_dir else DATA_DIR
    return base / rel_path


def _ensure_s3_file(settings: dict, rel_path: str) -> Path:
    local_path = _local_path(settings.get("cache_dir", ""), rel_path)
    if local_path.exists():
        return local_path

    local_path.parent.mkdir(parents=True, exist_ok=True)
    key = _s3_key(settings.get("prefix", ""), rel_path)
    client = _make_s3_client(settings)
    try:
        client.download_file(settings["bucket"], key, str(local_path))
    except (BotoCoreError, ClientError) as exc:
        raise FileNotFoundError(f"S3 download failed: s3://{settings['bucket']}/{key}") from exc
    return local_path


def _requested_snapshot_tag(params) -> str:
    requested_tag = os.getenv("MLCLUSTER_SNAPSHOT_TAG")
    if requested_tag is None or not requested_tag.strip():
        return params.snapshot.tag
    return requested_tag.strip()


def _manifest_key() -> str:
    return os.getenv("MLCLUSTER_S3_MANIFEST_KEY", "latest.json").strip("/")


def _resolve_snapshot_tag(params, s3_settings: dict | None) -> tuple[str, str]:
    requested_tag = _requested_snapshot_tag(params)
    if requested_tag != "latest":
        return requested_tag, requested_tag

    if not s3_settings:
        raise RuntimeError("MLCLUSTER_SNAPSHOT_TAG=latest requires MLCLUSTER_S3_BUCKET.")

    manifest = _read_s3_json(s3_settings, _manifest_key())
    resolved_tag = str(manifest.get("tag", "")).strip()
    if not resolved_tag:
        raise ValueError("S3 latest manifest must contain a non-empty 'tag' field.")
    return requested_tag, resolved_tag


def _reload_ttl_seconds() -> float:
    raw = os.getenv("MLCLUSTER_RELOAD_TTL_SECONDS", "300").strip()
    try:
        return max(0.0, float(raw))
    except ValueError:
        logger.warning("Invalid MLCLUSTER_RELOAD_TTL_SECONDS=%r; using 300 seconds.", raw)
        return 300.0


class AppStore:
    """
    Chứa toàn bộ dữ liệu phục vụ API.
    Với tag cố định: được load 1 lần khi app khởi động.
    Với tag latest: có thể reload khi S3 manifest đổi tag.
    """

    def __init__(self) -> None:
        params = load_params()
        s3_settings = _get_s3_settings()
        self.data_available: bool = False

        try:
            self.requested_tag, self.tag = _resolve_snapshot_tag(params, s3_settings)
        except (RuntimeError, FileNotFoundError, ValueError) as exc:
            logger.warning("Could not resolve snapshot tag: %s. Starting with empty store.", exc)
            self.requested_tag = "latest"
            self.tag = "none"
            self.source = "local"
            self._init_empty()
            return

        self.source = "s3" if s3_settings else "local"

        labels_rel = f"models/{self.tag}/best_labels.parquet"
        cluster_labels_rel = f"labels/{self.tag}/cluster_labels.json"
        tech_rel = f"raw/snapshot_{self.tag}/technologies.parquet"

        # --- best_labels: tech_id → cluster_id ---
        labels_path = (
            _ensure_s3_file(s3_settings, labels_rel)
            if s3_settings else DATA_DIR / labels_rel
        )
        if not Path(labels_path).exists():
            logger.warning(
                "ML clustering artifacts not found at %s. "
                "Run the DVC pipeline to generate data. Starting with empty store.",
                labels_path,
            )
            self._init_empty()
            return

        df_labels = pd.read_parquet(labels_path)
        self.data_available = True
        # cluster_id = -1 → noise
        self.labels_df: pd.DataFrame = df_labels  # cols: tech_id, cluster_id

        # --- cluster_labels: dict[str, dict] hoặc list[dict] ---
        labels_json = (
            _ensure_s3_file(s3_settings, cluster_labels_rel)
            if s3_settings else DATA_DIR / cluster_labels_rel
        )
        with open(labels_json, encoding="utf-8") as f:
            raw_labels = json.load(f)
        # Hỗ trợ cả 2 format: dict{"0": {...}} và list[{cluster_id: 0, ...}]
        if isinstance(raw_labels, dict):
            self.cluster_labels: dict[int, dict] = {
                int(k): v for k, v in raw_labels.items()
            }
        else:
            self.cluster_labels = {int(c["cluster_id"]): c for c in raw_labels}

        # --- technologies snapshot: tech_id → name ---
        tech_path = (
            _ensure_s3_file(s3_settings, tech_rel)
            if s3_settings else DATA_DIR / tech_rel
        )
        df_tech = pd.read_parquet(tech_path)
        # Tạo 2 index: tech_id→name và name_lower→tech_id (để lookup theo tên).
        # tech_id luôn ép về str để khớp schema (tránh lỗi validate Pydantic
        # nếu cột parquet là kiểu số) và để join nhất quán với labels_df.
        self.id_to_name: dict[str, str] = {
            str(tid): str(name) for tid, name in zip(df_tech["tech_id"], df_tech["name"])
        }
        self.name_lower_to_id: dict[str, str] = {
            n.lower(): tid for tid, n in self.id_to_name.items()
        }

        # --- Merge: tech_id → cluster_id (chỉ techs đã cluster, bỏ noise=-1) ---
        self.tech_to_cluster: dict[str, int] = {
            str(row["tech_id"]): int(row["cluster_id"])
            for _, row in df_labels.iterrows()
        }

        # --- Ngược lại: cluster_id → list tech names ---
        self.cluster_to_techs: dict[int, list[str]] = {}
        for tech_id, cid in self.tech_to_cluster.items():
            name = self.id_to_name.get(tech_id, tech_id)
            self.cluster_to_techs.setdefault(cid, []).append(name)

    def _init_empty(self) -> None:
        """Set empty data structures when artifacts are not available."""
        import pandas as _pd
        self.labels_df = _pd.DataFrame(columns=["tech_id", "cluster_id"])
        self.cluster_labels: dict[int, dict] = {}
        self.id_to_name: dict[str, str] = {}
        self.name_lower_to_id: dict[str, str] = {}
        self.tech_to_cluster: dict[str, int] = {}
        self.cluster_to_techs: dict[int, list[str]] = {}

    def lookup_tech(self, name: str) -> tuple[str | None, int | None]:
        """
        Tìm (tech_id, cluster_id) theo tên (case-insensitive).
        Trả về (None, None) nếu không tìm thấy.
        """
        tech_id = self.name_lower_to_id.get(name.lower())
        if tech_id is None:
            return None, None
        cluster_id = self.tech_to_cluster.get(tech_id)
        return tech_id, cluster_id

    def get_cluster_label(self, cluster_id: int) -> dict | None:
        return self.cluster_labels.get(cluster_id)


_STORE_LOCK = RLock()
_STORE: AppStore | None = None
_STORE_CHECKED_AT = 0.0


def get_store() -> AppStore:
    """Load store và tự reload theo TTL nếu đang dùng S3 latest manifest."""
    global _STORE, _STORE_CHECKED_AT

    now = monotonic()
    with _STORE_LOCK:
        if _STORE is None:
            _STORE = AppStore()
            _STORE_CHECKED_AT = now
            return _STORE

        ttl = _reload_ttl_seconds()
        if _STORE.requested_tag != "latest" or ttl <= 0 or now - _STORE_CHECKED_AT < ttl:
            return _STORE

        _STORE_CHECKED_AT = now
        params = load_params()
        s3_settings = _get_s3_settings()
        try:
            _, resolved_tag = _resolve_snapshot_tag(params, s3_settings)
        except Exception:
            logger.exception("Could not refresh ML clustering latest manifest; keeping tag=%s", _STORE.tag)
            return _STORE

        if resolved_tag != _STORE.tag:
            logger.info("Reloading ML clustering store: %s -> %s", _STORE.tag, resolved_tag)
            _STORE = AppStore()
        return _STORE
