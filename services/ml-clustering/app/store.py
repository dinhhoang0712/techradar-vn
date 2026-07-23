"""
AppStore — load và cache artifacts từ disk hoặc MinIO.

Artifacts:
  - best_labels.parquet  : tech_id → cluster_id
  - cluster_labels.json  : cluster_id → label metadata
  - technologies.parquet : tech_id → name (từ snapshot)

Gọi `get_store()` ở bất kỳ đâu. Nếu MLCLUSTER_SNAPSHOT_TAG=latest,
store sẽ đọc manifest trên MinIO và tự reload theo TTL khi tag đổi.
"""

from __future__ import annotations

import difflib
import json
import logging
import os
from datetime import UTC, datetime
from pathlib import Path
from threading import RLock
from time import monotonic

import boto3
import pandas as pd
from botocore.config import Config
from botocore.exceptions import BotoCoreError, ClientError
from conf.config import DATA_DIR, load_params

logger = logging.getLogger(__name__)

# MinIO luôn cần path-style addressing (không hỗ trợ virtual-hosted-style qua DNS wildcard).
_ADDRESSING_STYLE = "path"
_REGION = "us-east-1"  # không có ý nghĩa thật với MinIO, boto3 chỉ yêu cầu có giá trị.

# Ngưỡng difflib.SequenceMatcher.ratio() để chấp nhận 1 match "đủ giống" cho
# provisional lookup (xem AppStore.find_nearest_known_tech) — tuned thủ công:
# 0.72 bắt được biến thể viết hoa/thường/khoảng trắng ("nextjs" ~ "next.js") mà
# không quá lỏng tới mức khớp nhầm 2 tech không liên quan.
_PROVISIONAL_MATCH_THRESHOLD = 0.72


def _get_minio_settings() -> dict | None:
    bucket = os.getenv("MLCLUSTER_MINIO_BUCKET")
    if not bucket:
        return None

    prefix = os.getenv("MLCLUSTER_MINIO_PREFIX", "").strip("/")
    cache_dir = os.getenv("MLCLUSTER_MINIO_CACHE_DIR", "")
    endpoint_url = os.getenv("MLCLUSTER_MINIO_ENDPOINT")
    access_key = os.getenv("MLCLUSTER_MINIO_ACCESS_KEY")
    secret_key = os.getenv("MLCLUSTER_MINIO_SECRET_KEY")

    return {
        "bucket": bucket,
        "prefix": prefix,
        "cache_dir": cache_dir,
        "endpoint_url": endpoint_url,
        "access_key": access_key,
        "secret_key": secret_key,
    }


def _make_minio_client(settings: dict) -> boto3.client:
    return boto3.client(
        "s3",
        endpoint_url=settings.get("endpoint_url") or None,
        region_name=_REGION,
        aws_access_key_id=settings.get("access_key") or None,
        aws_secret_access_key=settings.get("secret_key") or None,
        config=Config(s3={"addressing_style": _ADDRESSING_STYLE}),
    )


def _minio_key(prefix: str, rel_path: str) -> str:
    if prefix:
        return f"{prefix}/{rel_path}"
    return rel_path


def _read_minio_json(settings: dict, rel_path: str) -> dict:
    key = _minio_key(settings.get("prefix", ""), rel_path.strip("/"))
    client = _make_minio_client(settings)
    try:
        obj = client.get_object(Bucket=settings["bucket"], Key=key)
        body = obj["Body"].read()
    except (BotoCoreError, ClientError) as exc:
        raise FileNotFoundError(f"MinIO manifest read failed: s3://{settings['bucket']}/{key}") from exc

    try:
        data = json.loads(body.decode("utf-8"))
    except json.JSONDecodeError as exc:
        raise ValueError(f"MinIO manifest is not valid JSON: s3://{settings['bucket']}/{key}") from exc

    if not isinstance(data, dict):
        raise ValueError(f"MinIO manifest must be a JSON object: s3://{settings['bucket']}/{key}")
    return data


def _write_minio_json(settings: dict, rel_path: str, data: dict) -> None:
    key = _minio_key(settings.get("prefix", ""), rel_path.strip("/"))
    client = _make_minio_client(settings)
    body = json.dumps(data, ensure_ascii=False, indent=2).encode("utf-8")
    try:
        client.put_object(Bucket=settings["bucket"], Key=key, Body=body, ContentType="application/json")
    except (BotoCoreError, ClientError) as exc:
        raise RuntimeError(f"MinIO write failed: s3://{settings['bucket']}/{key}") from exc


def _local_path(cache_dir: str, rel_path: str) -> Path:
    base = Path(cache_dir) if cache_dir else DATA_DIR
    return base / rel_path


def _ensure_minio_file(settings: dict, rel_path: str) -> Path:
    local_path = _local_path(settings.get("cache_dir", ""), rel_path)
    if local_path.exists():
        return local_path

    local_path.parent.mkdir(parents=True, exist_ok=True)
    key = _minio_key(settings.get("prefix", ""), rel_path)
    client = _make_minio_client(settings)
    try:
        client.download_file(settings["bucket"], key, str(local_path))
    except (BotoCoreError, ClientError) as exc:
        raise FileNotFoundError(f"MinIO download failed: s3://{settings['bucket']}/{key}") from exc
    return local_path


def _requested_snapshot_tag(params) -> str:
    requested_tag = os.getenv("MLCLUSTER_SNAPSHOT_TAG")
    if requested_tag is None or not requested_tag.strip():
        return params.snapshot.tag
    return requested_tag.strip()


def _manifest_key() -> str:
    return os.getenv("MLCLUSTER_MINIO_MANIFEST_KEY", "latest.json").strip("/")


def _resolve_snapshot_tag(params, minio_settings: dict | None) -> tuple[str, str]:
    requested_tag = _requested_snapshot_tag(params)
    if requested_tag != "latest":
        return requested_tag, requested_tag

    if not minio_settings:
        raise RuntimeError("MLCLUSTER_SNAPSHOT_TAG=latest requires MLCLUSTER_MINIO_BUCKET.")

    manifest = _read_minio_json(minio_settings, _manifest_key())
    resolved_tag = str(manifest.get("tag", "")).strip()
    if not resolved_tag:
        raise ValueError("MinIO latest manifest must contain a non-empty 'tag' field.")
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
    Với tag latest: có thể reload khi MinIO manifest đổi tag.
    """

    def __init__(self) -> None:
        params = load_params()
        minio_settings = _get_minio_settings()
        self.data_available: bool = False

        try:
            self.requested_tag, self.tag = _resolve_snapshot_tag(params, minio_settings)
        except (RuntimeError, FileNotFoundError, ValueError) as exc:
            logger.warning("Could not resolve snapshot tag: %s. Starting with empty store.", exc)
            self.requested_tag = "latest"
            self.tag = "none"
            self.source = "local"
            self._init_empty()
            return

        self.source = "minio" if minio_settings else "local"

        labels_rel = f"models/{self.tag}/best_labels.parquet"
        cluster_labels_rel = f"labels/{self.tag}/cluster_labels.json"
        tech_rel = f"raw/snapshot_{self.tag}/technologies.parquet"
        near_clusters_rel = f"models/{self.tag}/near_clusters.json"

        try:
            # --- best_labels: tech_id → cluster_id ---
            labels_path = _ensure_minio_file(minio_settings, labels_rel) if minio_settings else DATA_DIR / labels_rel
            if not Path(labels_path).exists():
                logger.warning(
                    "ML clustering artifacts not found at %s. "
                    "Run the DVC pipeline to generate data. Starting with empty store.",
                    labels_path,
                )
                self._init_empty()
                return

            df_labels = pd.read_parquet(labels_path)

            # --- cluster_labels: dict[str, dict] hoặc list[dict] ---
            labels_json = (
                _ensure_minio_file(minio_settings, cluster_labels_rel)
                if minio_settings
                else DATA_DIR / cluster_labels_rel
            )
            with open(labels_json, encoding="utf-8") as f:
                raw_labels = json.load(f)

            # --- technologies snapshot: tech_id → name ---
            tech_path = _ensure_minio_file(minio_settings, tech_rel) if minio_settings else DATA_DIR / tech_rel
            df_tech = pd.read_parquet(tech_path)
        except (BotoCoreError, ClientError, FileNotFoundError, OSError) as exc:
            logger.warning(
                "Could not load ML clustering artifacts (source=%s, tag=%s): %s. Starting with empty store.",
                self.source,
                self.tag,
                exc,
            )
            self._init_empty()
            return

        # --- near_clusters.json: tech_id → [{cluster_id, score}, ...] (tuỳ chọn) ---
        # Trước đây chỉ được log vào MLflow artifact, không route nào đọc lại —
        # có thể thiếu ở snapshot cũ hơn tính năng này; không coi là lỗi fatal.
        self.tech_near_clusters: dict[str, list[dict]] = {}
        try:
            near_clusters_path = (
                _ensure_minio_file(minio_settings, near_clusters_rel)
                if minio_settings
                else DATA_DIR / near_clusters_rel
            )
            if Path(near_clusters_path).exists():
                with open(near_clusters_path, encoding="utf-8") as f:
                    self.tech_near_clusters = json.load(f)
        except (BotoCoreError, ClientError, FileNotFoundError, OSError) as exc:
            logger.info("near_clusters.json not available (tag=%s): %s", self.tag, exc)

        self.data_available = True
        # cluster_id = -1 → noise
        self.labels_df: pd.DataFrame = df_labels  # cols: tech_id, cluster_id

        # Hỗ trợ cả 2 format: dict{"0": {...}} và list[{cluster_id: 0, ...}]
        if isinstance(raw_labels, dict):
            self.cluster_labels: dict[int, dict] = {int(k): v for k, v in raw_labels.items()}
        else:
            self.cluster_labels = {int(c["cluster_id"]): c for c in raw_labels}

        # --- cluster_overrides: admin-edited label metadata, layered on top of ---
        # --- the AI-generated cluster_labels above. Scoped per-tag like cluster_labels ---
        # --- itself, so an override never silently applies to a semantically different ---
        # --- cluster after a retrain reshuffles cluster ids. Optional — missing file is not fatal. ---
        overrides_rel = f"overrides/{self.tag}/cluster_overrides.json"
        self.cluster_overrides: dict[int, dict] = {}
        try:
            overrides_path = (
                _ensure_minio_file(minio_settings, overrides_rel) if minio_settings else DATA_DIR / overrides_rel
            )
            if Path(overrides_path).exists():
                with open(overrides_path, encoding="utf-8") as f:
                    raw_overrides = json.load(f)
                self.cluster_overrides = {int(k): v for k, v in raw_overrides.items()}
        except (BotoCoreError, ClientError, FileNotFoundError, OSError) as exc:
            logger.info("cluster_overrides.json not available (tag=%s): %s", self.tag, exc)

        for cid, override in self.cluster_overrides.items():
            if cid in self.cluster_labels:
                self.cluster_labels[cid] = {**self.cluster_labels[cid], **override, "overridden": True}

        # Tạo 2 index: tech_id→name và name_lower→tech_id (để lookup theo tên).
        # tech_id luôn ép về str để khớp schema (tránh lỗi validate Pydantic
        # nếu cột parquet là kiểu số) và để join nhất quán với labels_df.
        self.id_to_name: dict[str, str] = {
            str(tid): str(name) for tid, name in zip(df_tech["tech_id"], df_tech["name"])
        }
        self.name_lower_to_id: dict[str, str] = {n.lower(): tid for tid, n in self.id_to_name.items()}

        # --- Merge: tech_id → cluster_id (chỉ techs đã cluster, bỏ noise=-1) ---
        # membership_probability/outlier_score chỉ có khi model là hdbscan (gói
        # `hdbscan` gốc) — cột có thể thiếu hoặc toàn NaN với dbscan/kmeans.
        has_membership = "membership_probability" in df_labels.columns
        has_outlier = "outlier_score" in df_labels.columns
        self.tech_to_cluster: dict[str, int] = {}
        self.tech_membership_probability: dict[str, float] = {}
        self.tech_outlier_score: dict[str, float] = {}
        for _, row in df_labels.iterrows():
            tid = str(row["tech_id"])
            self.tech_to_cluster[tid] = int(row["cluster_id"])
            if has_membership and pd.notna(row["membership_probability"]):
                self.tech_membership_probability[tid] = float(row["membership_probability"])
            if has_outlier and pd.notna(row["outlier_score"]):
                self.tech_outlier_score[tid] = float(row["outlier_score"])

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
        self.cluster_overrides: dict[int, dict] = {}
        self.id_to_name: dict[str, str] = {}
        self.name_lower_to_id: dict[str, str] = {}
        self.tech_to_cluster: dict[str, int] = {}
        self.cluster_to_techs: dict[int, list[str]] = {}
        self.tech_near_clusters: dict[str, list[dict]] = {}
        self.tech_membership_probability: dict[str, float] = {}
        self.tech_outlier_score: dict[str, float] = {}

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

    def find_nearest_known_tech(self, name: str) -> tuple[str, int, float] | None:
        """
        Fallback khi `lookup_tech()` không khớp — tech thật sự mới (chưa từng có trong
        snapshot) phải đợi tới lần retrain kế tiếp mới được HDBSCAN phân cụm thật. Ở
        đây tìm tech ĐÃ BIẾT có tên giống nhất (difflib, so mặt chữ) để gán PROVISIONAL
        vào cùng cluster ngay — tốt hơn 404 trắng, dù kém chính xác hơn embedding
        ngữ nghĩa thật (E5). Cố tình KHÔNG dùng embedding model ở serving layer để giữ
        container nhẹ (`requirements-api.txt` không có torch/sentence-transformers).

        Trả (matched_name, cluster_id, score) hoặc None nếu không có ứng viên nào đạt
        `_PROVISIONAL_MATCH_THRESHOLD`, hoặc ứng viên tốt nhất lại là noise (cluster -1
        — gán "provisional noise" không phải tín hiệu hữu ích cho caller).
        """
        if not self.name_lower_to_id:
            return None

        target = name.lower().strip()
        candidates = difflib.get_close_matches(
            target, self.name_lower_to_id.keys(), n=1, cutoff=_PROVISIONAL_MATCH_THRESHOLD
        )
        if not candidates:
            return None

        matched_lower = candidates[0]
        tech_id = self.name_lower_to_id[matched_lower]
        cluster_id = self.tech_to_cluster.get(tech_id)
        if cluster_id is None or cluster_id == -1:
            return None

        score = difflib.SequenceMatcher(None, target, matched_lower).ratio()
        matched_name = self.id_to_name.get(tech_id, matched_lower)
        return matched_name, cluster_id, round(score, 3)

    def get_cluster_label(self, cluster_id: int) -> dict | None:
        return self.cluster_labels.get(cluster_id)

    def save_cluster_override(
        self,
        cluster_id: int,
        *,
        label: str | None = None,
        label_en: str | None = None,
        description: str | None = None,
        domain: str | None = None,
        actor: str | None = None,
    ) -> dict:
        """
        Ghi đè 1+ trường nhãn AI-generated cho 1 cluster: cập nhật in-memory ngay
        (admin thấy hiệu lực tức thì) và lưu bền vào overrides/{tag}/cluster_overrides.json
        (MinIO nếu bật, ngược lại local DATA_DIR) để sống sót qua reload/restart.

        Raise KeyError nếu cluster_id không tồn tại, ValueError nếu không có
        trường nào được truyền vào.
        """
        if cluster_id not in self.cluster_labels:
            raise KeyError(f"Cluster {cluster_id} không tồn tại (tag={self.tag})")

        fields = {
            k: v
            for k, v in {
                "label": label,
                "label_en": label_en,
                "description": description,
                "domain": domain,
            }.items()
            if v is not None
        }
        if not fields:
            raise ValueError("Cần ít nhất 1 trường để cập nhật (label/label_en/description/domain)")

        now = datetime.now(tz=UTC).isoformat()
        overrides_rel = f"overrides/{self.tag}/cluster_overrides.json"
        minio_settings = _get_minio_settings()

        with _STORE_LOCK:
            override = {
                **self.cluster_overrides.get(cluster_id, {}),
                **fields,
                "overridden_by": actor,
                "overridden_at": now,
            }
            self.cluster_overrides[cluster_id] = override
            payload = {str(k): v for k, v in self.cluster_overrides.items()}

            if minio_settings:
                _write_minio_json(minio_settings, overrides_rel, payload)
            else:
                local_path = DATA_DIR / overrides_rel
                local_path.parent.mkdir(parents=True, exist_ok=True)
                with open(local_path, "w", encoding="utf-8") as f:
                    json.dump(payload, f, ensure_ascii=False, indent=2)

            self.cluster_labels[cluster_id] = {
                **self.cluster_labels[cluster_id],
                **fields,
                "overridden": True,
                "overridden_by": actor,
                "overridden_at": now,
            }
            return self.cluster_labels[cluster_id]

    def get_near_clusters(self, tech_id: str) -> list[dict]:
        """Danh sách cụm "gần" (score >= near_cluster_threshold lúc train), đã
        enrich thêm label/label_en của cụm đó cho tiện hiển thị."""
        entries = self.tech_near_clusters.get(tech_id, [])
        enriched = []
        for entry in entries:
            cid = int(entry.get("cluster_id"))
            info = self.cluster_labels.get(cid, {})
            enriched.append(
                {
                    "cluster_id": cid,
                    "score": entry.get("score"),
                    "label": info.get("label"),
                    "label_en": info.get("label_en"),
                }
            )
        return enriched


_STORE_LOCK = RLock()
_STORE: AppStore | None = None
_STORE_CHECKED_AT = 0.0


def get_store() -> AppStore:
    """Load store và tự reload theo TTL nếu đang dùng MinIO latest manifest."""
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
        minio_settings = _get_minio_settings()
        try:
            _, resolved_tag = _resolve_snapshot_tag(params, minio_settings)
        except Exception:
            logger.exception("Could not refresh ML clustering latest manifest; keeping tag=%s", _STORE.tag)
            return _STORE

        if resolved_tag != _STORE.tag:
            logger.info("Reloading ML clustering store: %s -> %s", _STORE.tag, resolved_tag)
            _STORE = AppStore()
        return _STORE


def reset_store() -> None:
    """Force the next get_store() call to rebuild AppStore from disk/MinIO — used right after a
    pipeline retrain writes fresh artifacts, so serving doesn't wait out the TTL to see them."""
    global _STORE, _STORE_CHECKED_AT
    with _STORE_LOCK:
        _STORE = None
        _STORE_CHECKED_AT = 0.0
