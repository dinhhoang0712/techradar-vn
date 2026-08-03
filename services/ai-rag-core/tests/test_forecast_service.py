from datetime import UTC, datetime, timedelta

import pytest

import app.services.forecast_service as forecast_service  # type: ignore # noqa


def _recent_iso(days_ago: int) -> str:
    return (datetime.now(tz=UTC) - timedelta(days=days_ago)).strftime("%Y-%m-%d")


@pytest.mark.asyncio
async def test_get_sentiment_signal_counts_only_articles_inside_the_last_90_days(monkeypatch):
    # Regression: published_date is a plain string in Neo4j, so a Cypher
    # `date() - duration('P3M')` comparison always evaluates to NULL and silently drops every
    # row — filtering must happen here instead.
    rows = [
        {"published_date": _recent_iso(10), "sentiment_score": 0.5},
        {"published_date": _recent_iso(200), "sentiment_score": 0.9},  # outside the 90-day window
    ]

    async def fake_run_query(cypher, params=None):
        return rows

    monkeypatch.setattr(forecast_service, "run_query", fake_run_query)

    signal = await forecast_service._get_sentiment_signal("CI/CD")

    assert signal is not None
    assert "1" in signal.signal
    assert signal.value == pytest.approx(0.5)


@pytest.mark.asyncio
async def test_get_sentiment_signal_handles_mixed_date_formats(monkeypatch):
    rows = [
        {"published_date": _recent_iso(5), "sentiment_score": 0.4},
        {"published_date": "not-a-date", "sentiment_score": 0.9},
    ]

    async def fake_run_query(cypher, params=None):
        return rows

    monkeypatch.setattr(forecast_service, "run_query", fake_run_query)

    signal = await forecast_service._get_sentiment_signal("CI/CD")

    assert signal is not None
    assert signal.value == pytest.approx(0.4)


@pytest.mark.asyncio
async def test_get_sentiment_signal_defaults_to_zero_when_no_recent_article_has_a_sentiment_score(monkeypatch):
    rows = [{"published_date": _recent_iso(5), "sentiment_score": None}]

    async def fake_run_query(cypher, params=None):
        return rows

    monkeypatch.setattr(forecast_service, "run_query", fake_run_query)

    signal = await forecast_service._get_sentiment_signal("CI/CD")

    assert signal is not None
    assert signal.value == 0.0


@pytest.mark.asyncio
async def test_get_sentiment_signal_returns_none_when_no_articles_are_recent(monkeypatch):
    rows = [{"published_date": _recent_iso(200), "sentiment_score": 0.5}]

    async def fake_run_query(cypher, params=None):
        return rows

    monkeypatch.setattr(forecast_service, "run_query", fake_run_query)

    assert await forecast_service._get_sentiment_signal("CI/CD") is None


@pytest.mark.asyncio
async def test_get_sentiment_signal_returns_none_when_the_query_fails(monkeypatch):
    async def fake_run_query(cypher, params=None):
        raise ConnectionError("neo4j unreachable")

    monkeypatch.setattr(forecast_service, "run_query", fake_run_query)

    assert await forecast_service._get_sentiment_signal("CI/CD") is None
