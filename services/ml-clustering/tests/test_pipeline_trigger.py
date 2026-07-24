"""Kiểm tra cơ chế tự sinh snapshot tag khi trigger pipeline qua API (fix bug
auto-retrain hàng tuần luôn fail vì tag cố định trong params.yaml)."""

import json
import re

import pytest
import redis as redis_module

from app import routes_pipeline
from app.routes_pipeline import (
    _TAG_LINE_RE,
    _bump_snapshot_tag,
    _generate_snapshot_tag,
    _publish_completion,
)

_SAMPLE_PARAMS_YAML = """\
snapshot:
  tag: "2026-05-13"          # nhãn snapshot, gắn vào path data/<tag>/
  min_tech_degree: 1

features:
  use_job_tfidf: true
"""


def test_generate_snapshot_tag_format():
    """Tag sinh ra phải khớp mẫu YYYY-MM-DD-HHMM để đảm bảo unique theo phút."""
    tag = _generate_snapshot_tag()
    assert re.fullmatch(r"\d{4}-\d{2}-\d{2}-\d{4}", tag)


def test_bump_snapshot_tag_preserves_comments_and_other_keys(tmp_path):
    """Ghi tag mới nhưng KHÔNG được phá comment/format các dòng khác — nếu parse
    lại + dump YAML sẽ mất hết comment tiếng Việt trong params.yaml thật."""
    params_path = tmp_path / "params.yaml"
    params_path.write_text(_SAMPLE_PARAMS_YAML, encoding="utf-8")

    old_tag = _bump_snapshot_tag("2026-07-20-0600", params_path=params_path)

    assert old_tag == "2026-05-13"
    new_text = params_path.read_text(encoding="utf-8")
    assert 'tag: "2026-07-20-0600"' in new_text
    assert "# nhãn snapshot, gắn vào path data/<tag>/" in new_text
    assert "min_tech_degree: 1" in new_text
    assert "use_job_tfidf: true" in new_text


def test_bump_snapshot_tag_raises_when_no_tag_line(tmp_path):
    params_path = tmp_path / "params.yaml"
    params_path.write_text("snapshot:\n  min_tech_degree: 1\n", encoding="utf-8")

    with pytest.raises(RuntimeError):
        _bump_snapshot_tag("2026-07-20-0600", params_path=params_path)


def test_tag_line_regex_matches_only_snapshot_tag():
    """Đảm bảo regex không match nhầm dòng khác có chứa từ 'tag'."""
    text = 'snapshot:\n  tag: "abc"\nother:\n  some_tag_field: "xyz"\n'
    matches = _TAG_LINE_RE.findall(text)
    assert len(matches) == 1
    assert matches[0][1] == "abc"


class _FakeRedis:
    def __init__(self):
        self.published = None

    def publish(self, channel, message):
        self.published = (channel, message)


class _FailingRedis:
    def publish(self, channel, message):
        raise redis_module.exceptions.RedisError("connection refused")


def test_publish_completion_sends_current_state_to_redis(monkeypatch):
    fake = _FakeRedis()
    monkeypatch.setattr(routes_pipeline, "_redis_client", fake)
    with routes_pipeline._LOCK:
        routes_pipeline._state["status"] = "success"
        routes_pipeline._state["duration_s"] = 42
        routes_pipeline._state["snapshot_tag"] = "2026-07-24-0000"
        routes_pipeline._state["error"] = None

    _publish_completion()

    assert fake.published is not None
    channel, message = fake.published
    assert channel == "clustering:completed"
    assert json.loads(message) == {
        "status": "success",
        "duration_s": 42,
        "snapshot_tag": "2026-07-24-0000",
        "error": None,
    }


def test_publish_completion_swallows_redis_errors(monkeypatch):
    """Admin notification is a nice-to-have — a Redis outage must never crash the retrain."""
    monkeypatch.setattr(routes_pipeline, "_redis_client", _FailingRedis())
    with routes_pipeline._LOCK:
        routes_pipeline._state["status"] = "failed"
        routes_pipeline._state["error"] = "boom"

    _publish_completion()  # must not raise
