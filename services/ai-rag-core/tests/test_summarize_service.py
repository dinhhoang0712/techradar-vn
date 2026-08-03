import pytest

import app.services.summarize_service as summarize_service  # type: ignore # noqa


def test_parse_period_uses_the_real_last_day_of_30_day_months():
    # Regression: hardcoding "-31" produced invalid dates (e.g. 2026-04-31) that
    # Neo4j's date() rejects, causing _fetch_articles() to silently return [].
    start, end = summarize_service._parse_period("2026-04")
    assert start == "2026-04-01"
    assert end == "2026-04-30"


def test_parse_period_uses_the_real_last_day_of_february():
    start, end = summarize_service._parse_period("2026-02")
    assert start == "2026-02-01"
    assert end == "2026-02-28"


def test_parse_period_uses_the_real_last_day_of_february_in_a_leap_year():
    start, end = summarize_service._parse_period("2024-02")
    assert start == "2024-02-01"
    assert end == "2024-02-29"


def test_parse_period_handles_31_day_months():
    start, end = summarize_service._parse_period("2026-07")
    assert start == "2026-07-01"
    assert end == "2026-07-31"


def test_parse_period_handles_a_quarter_ending_in_a_30_day_month():
    start, end = summarize_service._parse_period("2026-Q2")
    assert start == "2026-04-01"
    assert end == "2026-06-30"


def test_parse_period_handles_a_year():
    start, end = summarize_service._parse_period("2026")
    assert start == "2026-01-01"
    assert end == "2026-12-31"


def _article(title: str, published_date: str) -> dict:
    return {"title": title, "content": "x", "published_date": published_date, "sentiment_score": 0.1}


@pytest.mark.asyncio
async def test_fetch_articles_keeps_only_rows_inside_the_date_range(monkeypatch):
    # Regression: published_date is a plain string in Neo4j, so a Cypher `date($start)` comparison
    # always evaluates to NULL and silently drops every row — filtering must happen here instead.
    rows = [
        _article("in range", "2026-05-15"),
        _article("before range", "2026-03-01"),
        _article("after range", "2026-08-01"),
    ]

    async def fake_run_query(cypher, params=None):
        return rows

    monkeypatch.setattr(summarize_service, "run_query", fake_run_query)

    result = await summarize_service._fetch_articles("CI/CD", "2026-04-01", "2026-07-31")

    assert [r["title"] for r in result] == ["in range"]


@pytest.mark.asyncio
async def test_fetch_articles_handles_mixed_date_formats_from_different_crawler_sources(monkeypatch):
    rows = [
        _article("iso", "2026-05-15"),
        _article("slash ddmmyyyy", "20/05/2026"),
        _article("unparseable", "not-a-date"),
    ]

    async def fake_run_query(cypher, params=None):
        return rows

    monkeypatch.setattr(summarize_service, "run_query", fake_run_query)

    result = await summarize_service._fetch_articles("CI/CD", "2026-04-01", "2026-07-31")

    assert {r["title"] for r in result} == {"iso", "slash ddmmyyyy"}


@pytest.mark.asyncio
async def test_fetch_articles_sorts_newest_first_and_caps_at_20(monkeypatch):
    rows = [_article(f"a{i}", f"2026-05-{i + 1:02d}") for i in range(25)]

    async def fake_run_query(cypher, params=None):
        return rows

    monkeypatch.setattr(summarize_service, "run_query", fake_run_query)

    result = await summarize_service._fetch_articles("CI/CD", "2026-04-01", "2026-07-31")

    assert len(result) == 20
    assert result[0]["title"] == "a24"  # 2026-05-25, the most recent
    assert result[-1]["title"] == "a5"  # 2026-05-06, the 20th most recent


@pytest.mark.asyncio
async def test_fetch_articles_returns_empty_list_when_the_query_fails(monkeypatch):
    async def fake_run_query(cypher, params=None):
        raise ConnectionError("neo4j unreachable")

    monkeypatch.setattr(summarize_service, "run_query", fake_run_query)

    assert await summarize_service._fetch_articles("CI/CD", "2026-04-01", "2026-07-31") == []
