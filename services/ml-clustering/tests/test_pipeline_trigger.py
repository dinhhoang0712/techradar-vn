"""Kiểm tra cơ chế tự sinh snapshot tag khi trigger pipeline qua API (fix bug
auto-retrain hàng tuần luôn fail vì tag cố định trong params.yaml)."""

import re

import pytest

from app.routes_pipeline import (
    _TAG_LINE_RE,
    _bump_snapshot_tag,
    _generate_snapshot_tag,
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
