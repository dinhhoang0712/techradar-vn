"""
Stage 06 — PUBLISH: đẩy artifact của 1 snapshot đã train lên MinIO rồi mới cập nhật
`latest.json` — bước "Publish Model / Artifact Deployment" còn thiếu giữa TRAIN và SERVING.

Trước đây việc này chỉ là 1 hàm phụ (`_publish_to_minio` trong app/routes_pipeline.py) chạy
sau TẤT CẢ 5 stage, kể cả stage_05_writeback đã ghi Neo4j — nên nếu publish thất bại, graph
đã bị đổi nhưng serving (AppStore) vẫn phục vụ tag cũ: 2 hệ thống lệch nhau. Ngoài ra hàm đó
không bao giờ ghi `latest.json`, nên chế độ `MLCLUSTER_SNAPSHOT_TAG=latest` (đọc bởi
app/store.py để hỗ trợ nhiều replica / auto-reload) là code chết — never actually written.

Stage này khắc phục cả 2 vấn đề:
  1. Đứng TRƯỚC stage_05_writeback trong pipeline (xem app/routes_pipeline.py) — Neo4j chỉ bị
     ghi sau khi artifact đã publish thành công.
  2. Chỉ ghi đè `latest.json` SAU KHI đã xác nhận (head_object) mọi artifact bắt buộc tồn tại
     đúng kích thước trên MinIO — nếu 1 upload thất bại/thiếu, `latest.json` giữ nguyên tag cũ,
     serving không bao giờ thấy 1 tag "publish nửa vời".

Đồng thời gate theo quyết định "champion" đã có sẵn ở stage_03_train (register_best_model):
model mới không tốt hơn champion hiện tại thì KHÔNG publish (tránh serving 1 model tệ hơn) —
trừ khi gọi với --force.

CLI:
    python -m pipelines.stage_06_publish --params params.yaml --run-id <best_run_id> [--force]

Output:
    MinIO: models/<tag>/*, labels/<tag>/*, raw/snapshot_<tag>/technologies.parquet (upload)
           <manifest_key> (mặc định latest.json) — {tag, published_at, previous_tag, run_id, ...}
"""

from __future__ import annotations

import json
from datetime import UTC, datetime
from pathlib import Path
from typing import Any

import typer
from loguru import logger

app = typer.Typer(add_completion=False, help="Publish snapshot artifacts to MinIO + flip latest.json")

# Cùng danh sách rel_path mà AppStore (app/store.py) đọc cho mỗi tag khi serving qua MinIO.
# near_clusters.json optional với AppStore nên thiếu file không chặn publish.
_REQUIRED_REL_PATHS = [
    "models/{tag}/best_labels.parquet",
    "labels/{tag}/cluster_labels.json",
    "raw/snapshot_{tag}/technologies.parquet",
]
_OPTIONAL_REL_PATHS = [
    "models/{tag}/near_clusters.json",
]


class PublishError(RuntimeError):
    """Raised khi 1+ artifact bắt buộc thiếu/upload lỗi — latest.json KHÔNG bị đổi khi lỗi này."""


def _verify_uploaded(client, bucket: str, key: str, expected_size: int) -> None:
    """head_object sau upload — bắt các trường hợp upload_file() không raise nhưng object
    không thực sự tồn tại đúng kích thước trên MinIO (network glitch, bucket policy...)."""
    from botocore.exceptions import BotoCoreError, ClientError

    try:
        head = client.head_object(Bucket=bucket, Key=key)
    except (BotoCoreError, ClientError) as exc:
        raise PublishError(f"head_object thất bại sau upload: s3://{bucket}/{key}: {exc}") from exc

    actual_size = head.get("ContentLength")
    if actual_size != expected_size:
        raise PublishError(
            f"Kích thước lệch sau upload s3://{bucket}/{key}: local={expected_size} remote={actual_size}"
        )


def publish_snapshot(
    tag: str,
    *,
    data_dir: Path,
    minio_settings: dict,
    run_id: str | None = None,
    extra_manifest_fields: dict[str, Any] | None = None,
) -> dict:
    """
    Upload artifact của `tag` lên MinIO rồi ghi đè manifest — raise PublishError nếu thiếu/lỗi
    1 artifact bắt buộc (KHÔNG ghi manifest trong trường hợp đó). Trả về manifest dict đã ghi.
    """
    from app.store import _make_minio_client, _manifest_key, _minio_key, _read_minio_json, _write_minio_json

    client = _make_minio_client(minio_settings)
    bucket = minio_settings["bucket"]
    prefix = minio_settings.get("prefix", "")

    uploaded: list[str] = []
    missing_required: list[str] = []

    for rel_template in _REQUIRED_REL_PATHS + _OPTIONAL_REL_PATHS:
        rel_path = rel_template.format(tag=tag)
        local_path = data_dir / rel_path
        is_required = rel_template in _REQUIRED_REL_PATHS

        if not local_path.exists():
            if is_required:
                missing_required.append(rel_path)
            else:
                logger.info("Publish: bỏ qua {} (không tồn tại — optional)", rel_path)
            continue

        key = _minio_key(prefix, rel_path)
        size = local_path.stat().st_size
        try:
            client.upload_file(str(local_path), bucket, key)
        except Exception as exc:
            raise PublishError(f"Upload {rel_path} lên s3://{bucket}/{key} thất bại: {exc}") from exc

        _verify_uploaded(client, bucket, key, size)
        uploaded.append(rel_path)
        logger.info("Publish: {} -> s3://{}/{} ({} bytes, verified)", rel_path, bucket, key, size)

    if missing_required:
        raise PublishError(f"Thiếu artifact bắt buộc cho tag={tag}: {missing_required}")
    if not uploaded:
        raise PublishError(f"Không có artifact nào cho tag={tag} để publish")

    # Đọc manifest cũ TRƯỚC khi ghi đè — previous_tag phục vụ audit/rollback thủ công.
    previous_tag: str | None = None
    try:
        old_manifest = _read_minio_json(minio_settings, _manifest_key())
        previous_tag = str(old_manifest.get("tag", "") or "").strip() or None
    except (FileNotFoundError, ValueError) as exc:
        logger.info("Không có manifest cũ (lần publish đầu tiên?): {}", exc)

    manifest = {
        "tag": tag,
        "published_at": datetime.now(tz=UTC).isoformat(),
        "previous_tag": previous_tag,
        "run_id": run_id,
        "uploaded_artifacts": uploaded,
        **(extra_manifest_fields or {}),
    }
    _write_minio_json(minio_settings, _manifest_key(), manifest)
    logger.info(
        "latest.json cập nhật: {} -> {} (uploaded={} files)",
        previous_tag or "(none)",
        tag,
        len(uploaded),
    )
    return manifest


def _load_best_metrics(data_dir: Path, tag: str) -> dict[str, Any]:
    """best_metrics.json (ghi bởi stage_03_train) — kèm vào manifest cho auditability, không
    fatal nếu thiếu (vd publish lại 1 tag cũ mà thư mục metrics đã bị dọn)."""
    path = data_dir / "metrics" / tag / "best_metrics.json"
    if not path.exists():
        return {}
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        logger.warning("Không đọc được {}: {}", path, exc)
        return {}


@app.command()
def main(
    params: str = typer.Option("params.yaml", help="Đường dẫn params.yaml"),
    run_id: str = typer.Option(..., help="MLflow run_id của best run (stage_03_train) — dùng để kiểm tra champion"),
    force: bool = typer.Option(
        False,
        "--force/--no-force",
        help="Bỏ qua champion gate — publish dù model mới không tốt hơn champion hiện tại",
    ),
) -> None:
    from conf.config import DATA_DIR, load_params

    from app.store import _get_minio_settings

    params_obj = load_params(params)
    tag = params_obj.snapshot.tag
    logger.info("Stage 06 — PUBLISH | tag={} run_id={} force={}", tag, run_id, force)

    minio_settings = _get_minio_settings()
    if minio_settings is None:
        logger.info("MLCLUSTER_MINIO_BUCKET không được set — bỏ qua publish (deployment chỉ đọc local disk).")
        raise typer.Exit(code=0)

    if not force:
        from src.tracking.mlflow_logger import is_run_promoted_to_champion

        promoted = is_run_promoted_to_champion(run_id, params_obj.mlflow.tracking_uri)
        if not promoted:
            logger.warning(
                "Model của run {} KHÔNG được promote lên champion (tệ hơn champion hiện tại) — "
                "bỏ qua publish. Dùng --force để ghi đè nếu thật sự muốn deploy model này.",
                run_id,
            )
            raise typer.Exit(code=0)

    try:
        manifest = publish_snapshot(
            tag,
            data_dir=DATA_DIR,
            minio_settings=minio_settings,
            run_id=run_id,
            extra_manifest_fields=_load_best_metrics(DATA_DIR, tag),
        )
    except PublishError as exc:
        logger.error("Publish thất bại — latest.json KHÔNG bị đổi, serving vẫn dùng tag cũ: {}", exc)
        raise typer.Exit(code=1) from exc

    print(f"\n{'=' * 55}")
    print("  Stage 06 PUBLISH hoàn tất")
    print(f"{'=' * 55}")
    print(f"  Tag              : {manifest['tag']}")
    print(f"  Previous tag     : {manifest['previous_tag']}")
    print(f"  Uploaded         : {len(manifest['uploaded_artifacts'])} files")
    print(f"  Published at     : {manifest['published_at']}")
    print(f"{'=' * 55}\n")

    return 0


if __name__ == "__main__":
    app()
