"""Kiểm tra stage_06_publish — bước 'Publish Model / Artifact Deployment' còn thiếu giữa
TRAIN và SERVING: upload artifact lên MinIO rồi mới cập nhật latest.json, CHỈ khi mọi artifact
bắt buộc đã verify tồn tại đúng kích thước (không bao giờ để latest.json trỏ tới 1 tag publish
nửa vời)."""

import importlib
from pathlib import Path
from unittest.mock import MagicMock, patch

import pytest
import typer
from botocore.exceptions import ClientError

from pipelines.stage_06_publish import PublishError, main, publish_snapshot


class _FakeMinioClient:
    """Giả lập boto3 S3 client đủ để test upload_file + head_object mà không cần MinIO thật."""

    def __init__(self, fail_keys: set[str] | None = None):
        self._uploaded_sizes: dict[str, int] = {}
        self._fail_keys = fail_keys or set()

    def upload_file(self, local_path, bucket, key):
        if key in self._fail_keys:
            raise RuntimeError(f"simulated upload failure for {key}")
        self._uploaded_sizes[key] = Path(local_path).stat().st_size

    def head_object(self, Bucket, Key):
        if Key not in self._uploaded_sizes:
            raise ClientError({"Error": {"Code": "404", "Message": "Not Found"}}, "HeadObject")
        return {"ContentLength": self._uploaded_sizes[Key]}


@pytest.fixture
def fake_minio(monkeypatch):
    """Patch các helper MinIO của app.store (import trễ bên trong publish_snapshot) bằng bản
    giả lập in-memory — không cần MinIO thật, không cần network.

    Resolve `app.store` qua `importlib.import_module` NGAY TRONG fixture (execution time),
    không phải `from app import store` ở module top-level (collection time): test_pipeline_runs.py
    force-reimport `app.store` (thay hẳn object trong sys.modules) lúc COLLECTION — nếu ta bind
    tên module ở top-level, tuỳ thứ tự pytest collect các file test mà tên đó có thể trỏ tới
    bản app.store CŨ, khác với bản `publish_snapshot()` thực sự import lúc chạy test, khiến patch
    vô tác dụng (rơi vào boto3 client thật, lỗi 'Unable to locate credentials')."""
    manifests: dict[str, dict] = {}
    client_holder: dict[str, _FakeMinioClient] = {"client": _FakeMinioClient()}

    def fake_make_client(settings):
        return client_holder["client"]

    def fake_read_json(settings, rel_path):
        if rel_path not in manifests:
            raise FileNotFoundError(rel_path)
        return manifests[rel_path]

    def fake_write_json(settings, rel_path, data):
        manifests[rel_path] = data

    store_mod = importlib.import_module("app.store")
    monkeypatch.setattr(store_mod, "_make_minio_client", fake_make_client)
    monkeypatch.setattr(store_mod, "_read_minio_json", fake_read_json)
    monkeypatch.setattr(store_mod, "_write_minio_json", fake_write_json)
    monkeypatch.setattr(store_mod, "_minio_key", lambda prefix, rel: f"{prefix}/{rel}" if prefix else rel)
    monkeypatch.setattr(store_mod, "_manifest_key", lambda: "latest.json")

    return {"manifests": manifests, "client_holder": client_holder}


def _write_required_artifacts(data_dir: Path, tag: str) -> None:
    (data_dir / "models" / tag).mkdir(parents=True, exist_ok=True)
    (data_dir / "labels" / tag).mkdir(parents=True, exist_ok=True)
    (data_dir / "raw" / f"snapshot_{tag}").mkdir(parents=True, exist_ok=True)

    (data_dir / "models" / tag / "best_labels.parquet").write_bytes(b"parquet-bytes")
    (data_dir / "labels" / tag / "cluster_labels.json").write_text("{}", encoding="utf-8")
    (data_dir / "raw" / f"snapshot_{tag}" / "technologies.parquet").write_bytes(b"tech-parquet-bytes")


def test_publish_uploads_and_writes_manifest_after_all_required_verified(tmp_path, fake_minio):
    tag = "2026-07-27-1000"
    _write_required_artifacts(tmp_path, tag)

    manifest = publish_snapshot(
        tag,
        data_dir=tmp_path,
        minio_settings={"bucket": "b", "prefix": "ml-clustering"},
        run_id="run-abc",
    )

    assert manifest["tag"] == tag
    assert manifest["previous_tag"] is None
    assert manifest["run_id"] == "run-abc"
    assert len(manifest["uploaded_artifacts"]) == 3  # near_clusters.json optional, không tồn tại
    assert fake_minio["manifests"]["latest.json"]["tag"] == tag


def test_publish_captures_previous_tag_from_existing_manifest(tmp_path, fake_minio):
    fake_minio["manifests"]["latest.json"] = {"tag": "2026-07-20-0900"}
    tag = "2026-07-27-1000"
    _write_required_artifacts(tmp_path, tag)

    manifest = publish_snapshot(tag, data_dir=tmp_path, minio_settings={"bucket": "b", "prefix": ""}, run_id="r1")

    assert manifest["previous_tag"] == "2026-07-20-0900"
    assert fake_minio["manifests"]["latest.json"]["tag"] == tag


def test_publish_includes_optional_near_clusters_when_present(tmp_path, fake_minio):
    tag = "2026-07-27-1000"
    _write_required_artifacts(tmp_path, tag)
    (tmp_path / "models" / tag / "near_clusters.json").write_text("{}", encoding="utf-8")

    manifest = publish_snapshot(tag, data_dir=tmp_path, minio_settings={"bucket": "b", "prefix": ""}, run_id="r1")

    assert len(manifest["uploaded_artifacts"]) == 4
    assert "models/{}/near_clusters.json".format(tag) in manifest["uploaded_artifacts"]


def test_publish_raises_and_does_not_touch_manifest_when_required_artifact_missing(tmp_path, fake_minio):
    """cluster_labels.json bị thiếu — KHÔNG được upload xong rồi mới phát hiện nửa chừng,
    và latest.json phải giữ nguyên (hoặc không được tạo) để serving không thấy tag hỏng."""
    tag = "2026-07-27-1000"
    _write_required_artifacts(tmp_path, tag)
    (tmp_path / "labels" / tag / "cluster_labels.json").unlink()

    with pytest.raises(PublishError, match="Thiếu artifact bắt buộc"):
        publish_snapshot(tag, data_dir=tmp_path, minio_settings={"bucket": "b", "prefix": ""}, run_id="r1")

    assert "latest.json" not in fake_minio["manifests"]


def test_publish_raises_when_upload_fails_partway(tmp_path, fake_minio):
    """1 file upload lỗi (network glitch...) giữa chừng — latest.json KHÔNG được ghi, dù các
    file trước đó đã upload thành công (artifact 'nửa vời' trên MinIO nhưng manifest chưa trỏ tới)."""
    tag = "2026-07-27-1000"
    _write_required_artifacts(tmp_path, tag)
    failing_key = "labels/{}/cluster_labels.json".format(tag)
    fake_minio["client_holder"]["client"] = _FakeMinioClient(fail_keys={failing_key})

    with pytest.raises(PublishError, match="thất bại"):
        publish_snapshot(tag, data_dir=tmp_path, minio_settings={"bucket": "b", "prefix": ""}, run_id="r1")

    assert "latest.json" not in fake_minio["manifests"]


def test_publish_raises_when_head_object_size_mismatch(tmp_path, fake_minio, monkeypatch):
    """upload_file() không raise nhưng object thực tế trên MinIO khác kích thước local —
    head_object verify phải bắt được, không chỉ tin tưởng upload_file() không lỗi."""
    tag = "2026-07-27-1000"
    _write_required_artifacts(tmp_path, tag)

    class _MismatchClient(_FakeMinioClient):
        def head_object(self, Bucket, Key):
            result = super().head_object(Bucket=Bucket, Key=Key)
            return {"ContentLength": result["ContentLength"] + 1}

    fake_minio["client_holder"]["client"] = _MismatchClient()

    with pytest.raises(PublishError, match="Kích thước lệch"):
        publish_snapshot(tag, data_dir=tmp_path, minio_settings={"bucket": "b", "prefix": ""}, run_id="r1")

    assert "latest.json" not in fake_minio["manifests"]


def test_publish_raises_when_no_artifacts_at_all(tmp_path, fake_minio):
    tag = "2026-07-27-1000"
    (tmp_path / "models" / tag).mkdir(parents=True)
    (tmp_path / "labels" / tag).mkdir(parents=True)
    (tmp_path / "raw" / f"snapshot_{tag}").mkdir(parents=True)

    with pytest.raises(PublishError):
        publish_snapshot(tag, data_dir=tmp_path, minio_settings={"bucket": "b", "prefix": ""}, run_id="r1")


# ---------------------------------------------------------------------------
# CLI-level gating: main() — champion gate + MinIO-not-configured no-op
# ---------------------------------------------------------------------------


def test_main_exits_zero_without_publishing_when_minio_not_configured(monkeypatch):
    # main() imports load_params/DATA_DIR from conf.config LOCALLY (bên trong hàm, không phải
    # module-level) — phải patch tại conf.config, patch pipelines.stage_06_publish.load_params
    # sẽ AttributeError vì tên đó không tồn tại ở module-level trong file này.
    monkeypatch.delenv("MLCLUSTER_MINIO_BUCKET", raising=False)

    with patch("conf.config.load_params") as mock_load_params, patch(
        "pipelines.stage_06_publish.publish_snapshot"
    ) as mock_publish:
        mock_load_params.return_value = MagicMock(snapshot=MagicMock(tag="2026-07-27-1000"))
        with pytest.raises(typer.Exit) as exc_info:
            main(params="params.yaml", run_id="run-1", force=False)

    assert exc_info.value.exit_code == 0
    mock_publish.assert_not_called()


def test_main_skips_publish_when_not_promoted_and_not_forced(monkeypatch):
    monkeypatch.setenv("MLCLUSTER_MINIO_BUCKET", "test-bucket")

    with patch("conf.config.load_params") as mock_load_params, patch(
        "src.tracking.mlflow_logger.is_run_promoted_to_champion", return_value=False
    ) as mock_promoted, patch("pipelines.stage_06_publish.publish_snapshot") as mock_publish:
        mock_load_params.return_value = MagicMock(
            snapshot=MagicMock(tag="2026-07-27-1000"),
            mlflow=MagicMock(tracking_uri="http://mlflow"),
        )
        with pytest.raises(typer.Exit) as exc_info:
            main(params="params.yaml", run_id="run-1", force=False)

    assert exc_info.value.exit_code == 0
    mock_promoted.assert_called_once_with("run-1", "http://mlflow")
    mock_publish.assert_not_called()


def test_main_force_bypasses_champion_gate(monkeypatch):
    monkeypatch.setenv("MLCLUSTER_MINIO_BUCKET", "test-bucket")

    with patch("conf.config.load_params") as mock_load_params, patch(
        "src.tracking.mlflow_logger.is_run_promoted_to_champion"
    ) as mock_promoted, patch("pipelines.stage_06_publish.publish_snapshot") as mock_publish:
        mock_load_params.return_value = MagicMock(
            snapshot=MagicMock(tag="2026-07-27-1000"),
            mlflow=MagicMock(tracking_uri="http://mlflow"),
        )
        mock_publish.return_value = {
            "tag": "2026-07-27-1000",
            "previous_tag": None,
            "uploaded_artifacts": ["a", "b"],
            "published_at": "2026-07-27T10:00:00+00:00",
        }
        main(params="params.yaml", run_id="run-1", force=True)

    mock_promoted.assert_not_called()
    mock_publish.assert_called_once()


def test_main_exits_one_when_publish_fails(monkeypatch):
    monkeypatch.setenv("MLCLUSTER_MINIO_BUCKET", "test-bucket")

    with patch("conf.config.load_params") as mock_load_params, patch(
        "src.tracking.mlflow_logger.is_run_promoted_to_champion", return_value=True
    ), patch("pipelines.stage_06_publish.publish_snapshot", side_effect=PublishError("boom")):
        mock_load_params.return_value = MagicMock(
            snapshot=MagicMock(tag="2026-07-27-1000"),
            mlflow=MagicMock(tracking_uri="http://mlflow"),
        )
        with pytest.raises(typer.Exit) as exc_info:
            main(params="params.yaml", run_id="run-1", force=False)

    assert exc_info.value.exit_code == 1
