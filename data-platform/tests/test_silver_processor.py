from __future__ import annotations

from datetime import UTC, datetime

import pytest
import silver.processor as processor
from common import tech_alias_cache


@pytest.fixture(autouse=True)
def _reset_tech_alias_cache():
    """Cô lập state module-level của tech_alias_cache giữa các test."""
    saved = dict(tech_alias_cache._alias_by_normalized)
    yield
    tech_alias_cache._alias_by_normalized = saved


class FakeCursor:
    def __init__(self, fetchone_result=None):
        self.executed = []
        self._fetchone_result = fetchone_result

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc, tb):
        return False

    def execute(self, query, params=None):
        self.executed.append((query, params))

    def fetchone(self):
        return self._fetchone_result


class FakeConn:
    def __init__(self, fetchone_result=None):
        self._fetchone_result = fetchone_result
        self.cursors = []
        self.commit_count = 0

    def cursor(self):
        cur = FakeCursor(self._fetchone_result)
        self.cursors.append(cur)
        return cur

    def commit(self):
        self.commit_count += 1


def test_quality_score_thresholds():
    assert processor._quality_score("", "") == 0.0
    assert processor._quality_score("Short but 10+", "") == 0.3
    assert processor._quality_score("Short but 10+", "x" * 200) == 0.7
    assert processor._quality_score("Short but 10+", "x" * 800) == 1.0


def test_quality_score_ignores_titles_under_ten_chars():
    assert processor._quality_score("short", "x" * 800) == 0.7


def test_parse_published_at_supports_all_known_formats():
    expected = datetime(2026, 4, 11, tzinfo=UTC)
    assert processor._parse_published_at("2026-04-11") == expected
    assert processor._parse_published_at("11/04/2026") == expected
    assert processor._parse_published_at("2026-04-11T00:00:00") == expected
    assert processor._parse_published_at("2026-04-11 00:00:00") == expected


def test_parse_published_at_returns_none_for_missing_or_invalid():
    assert processor._parse_published_at("") is None
    assert processor._parse_published_at(None) is None
    assert processor._parse_published_at("not-a-date") is None


def test_process_article_extracts_nested_entities_and_inserts():
    conn = FakeConn(fetchone_result=None)
    msg = {
        "data": {
            "source_url": "https://example.com/a1",
            "title": "Python và Kubernetes bùng nổ tại VN",
            "content": "x" * 250,
            "published_at": "2026-04-11",
            "source_platform": "VN-Express",
            "entities": {"tech": ["Python", "Kubernetes"], "org": ["FPT"]},
        }
    }

    processor._process_article(conn, msg)

    assert conn.commit_count == 1
    query, params = conn.cursors[-1].executed[-1]
    assert "INSERT INTO dp_processed_articles" in query
    (
        article_id,
        source_url,
        source_platform,
        title,
        content,
        published_at,
        techs,
        orgs,
        locs,
        chash,
        is_dup,
        dup_of,
        quality,
    ) = params
    assert source_url == "https://example.com/a1"
    assert source_platform == "VN-Express"
    assert techs == ["Python", "Kubernetes"]
    assert orgs == ["FPT"]
    assert locs == []
    assert is_dup is False
    assert dup_of is None
    assert quality == 0.7


def test_process_article_prefers_flat_entity_fields_over_nested():
    conn = FakeConn(fetchone_result=None)
    msg = {
        "source_url": "https://example.com/a2",
        "title": "Flat format article",
        "content": "content",
        "entity_techs": ["Go"],
        "entities": {"tech": ["Python"]},
    }

    processor._process_article(conn, msg)

    _, params = conn.cursors[-1].executed[-1]
    techs = params[6]
    assert techs == ["Go"]


def test_process_article_skips_when_source_url_missing():
    conn = FakeConn()
    processor._process_article(conn, {"data": {"title": "No URL here"}})
    assert conn.cursors == []
    assert conn.commit_count == 0


def test_process_article_marks_duplicate_when_hash_already_exists():
    conn = FakeConn(fetchone_result={"id": "existing-id"})
    msg = {"data": {"source_url": "https://example.com/a3", "title": "T", "content": "C"}}

    processor._process_article(conn, msg)

    _, params = conn.cursors[-1].executed[-1]
    is_dup, dup_of = params[10], params[11]
    assert is_dup is True
    assert dup_of == "existing-id"


def test_process_job_extracts_company_from_nested_object():
    conn = FakeConn(fetchone_result=None)
    msg = {
        "data": {
            "job": {
                "source_url": "https://example.com/job-1",
                "title": "Senior Backend Engineer",
                "description": "Cần Python",
            },
            "company": {"name": "FPT", "location": "Hanoi"},
            "skills": ["Python"],
            "technologies": ["Python"],
        }
    }

    processor._process_job(conn, msg)

    assert conn.commit_count == 1
    query, params = conn.cursors[-1].executed[-1]
    assert "INSERT INTO dp_processed_jobs" in query
    (
        job_id,
        source_url,
        source_platform,
        title,
        company_name,
        company_location,
        salary,
        desc,
        req,
        benefit,
        skills,
        techs,
        chash,
        is_dup,
        quality,
        company_industry,
        company_size,
    ) = params
    assert company_name == "FPT"
    assert company_location == "Hanoi"
    assert skills == ["Python"]
    assert techs == ["Python"]


def test_process_job_prefers_flat_fields_over_nested_company():
    conn = FakeConn(fetchone_result=None)
    msg = {
        "job_title": "Data Analyst",
        "source_url": "https://example.com/job-2",
        "company_name": "OpenAI",
        "company": {"name": "Should be ignored"},
    }

    processor._process_job(conn, msg)

    _, params = conn.cursors[-1].executed[-1]
    assert params[3] == "Data Analyst"
    assert params[4] == "OpenAI"


def test_process_job_skips_when_source_url_missing():
    conn = FakeConn()
    processor._process_job(conn, {"data": {"job": {"title": "No URL"}}})
    assert conn.cursors == []
    assert conn.commit_count == 0


def test_process_article_canonicalizes_entity_techs_via_alias_cache():
    tech_alias_cache._alias_by_normalized = {"golang": "Go"}
    conn = FakeConn(fetchone_result=None)
    msg = {
        "data": {
            "source_url": "https://example.com/a4",
            "title": "Bai viet ve Golang",
            "content": "x" * 250,
            "entities": {"tech": ["Golang", "Docker"]},
        }
    }

    processor._process_article(conn, msg)

    techs = conn.cursors[-1].executed[-1][1][6]
    assert techs == ["Go", "Docker"]


def test_process_article_dedups_when_alias_and_raw_name_both_present():
    tech_alias_cache._alias_by_normalized = {"golang": "Go"}
    conn = FakeConn(fetchone_result=None)
    msg = {
        "data": {
            "source_url": "https://example.com/a5",
            "title": "T",
            "content": "C",
            "entity_techs": ["Golang", "Go"],
        }
    }

    processor._process_article(conn, msg)

    techs = conn.cursors[-1].executed[-1][1][6]
    assert techs == ["Go"]


def test_process_job_canonicalizes_technologies_via_alias_cache():
    tech_alias_cache._alias_by_normalized = {"ml": "Machine Learning"}
    conn = FakeConn(fetchone_result=None)
    msg = {
        "data": {
            "job": {"source_url": "https://example.com/job-3", "title": "ML Engineer"},
            "technologies": ["ML", "Python"],
        }
    }

    processor._process_job(conn, msg)

    techs = conn.cursors[-1].executed[-1][1][11]
    assert techs == ["Machine Learning", "Python"]
