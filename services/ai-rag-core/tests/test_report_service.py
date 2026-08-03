import pytest

import app.services.report_service as report_service  # type: ignore # noqa


def test_parse_period_dates_uses_the_real_last_day_of_30_day_months():
    # Regression: hardcoding "-31" broke asyncpg date parsing for Apr/Jun/Sep/Nov (30 days).
    start, end = report_service._parse_period_dates("2026-06")
    assert start == "2026-06-01"
    assert end == "2026-06-30"


def test_parse_period_dates_uses_the_real_last_day_of_february():
    start, end = report_service._parse_period_dates("2026-02")
    assert start == "2026-02-01"
    assert end == "2026-02-28"


def test_parse_period_dates_handles_31_day_months():
    start, end = report_service._parse_period_dates("2026-07")
    assert start == "2026-07-01"
    assert end == "2026-07-31"


def test_parse_period_dates_handles_a_quarter_ending_in_a_30_day_month():
    # Q2 = Apr-Jun, ends in June (30 days).
    start, end = report_service._parse_period_dates("2026-Q2")
    assert start == "2026-04-01"
    assert end == "2026-06-30"


def test_parse_period_dates_handles_a_year():
    start, end = report_service._parse_period_dates("2026")
    assert start == "2026-01-01"
    assert end == "2026-12-31"


class _FakeResponse:
    def __init__(self, payload):
        self._payload = payload

    def raise_for_status(self):
        pass

    def json(self):
        return self._payload


class _FakeAsyncClient:
    last_request = None

    def __init__(self, *args, **kwargs):
        pass

    async def __aenter__(self):
        return self

    async def __aexit__(self, *args):
        return False

    async def post(self, url, json=None):
        _FakeAsyncClient.last_request = (url, json)
        return _FakeResponse(_FakeAsyncClient.response_payload)


@pytest.mark.asyncio
async def test_fetch_cluster_labels_keeps_only_found_techs_with_a_label(monkeypatch):
    _FakeAsyncClient.response_payload = {
        "results": [
            {"tech_name": "Kafka", "found": True, "label": "Streaming/Event-driven"},
            {"tech_name": "Foo", "found": False, "label": None},
            {"tech_name": "Bar", "found": True, "label": None},
        ],
        "n_found": 2,
        "n_not_found": 1,
        "snapshot_tag": "test",
    }
    monkeypatch.setattr(report_service.httpx, "AsyncClient", _FakeAsyncClient)

    labels = await report_service._fetch_cluster_labels(["Kafka", "Foo", "Bar"])

    assert labels == {"Kafka": "Streaming/Event-driven"}
    assert _FakeAsyncClient.last_request[1] == {"tech_names": ["Kafka", "Foo", "Bar"]}


@pytest.mark.asyncio
async def test_fetch_cluster_labels_returns_empty_dict_for_no_names():
    assert await report_service._fetch_cluster_labels([]) == {}


@pytest.mark.asyncio
async def test_fetch_cluster_labels_fails_soft_when_ml_clustering_is_unreachable(monkeypatch):
    class _BoomAsyncClient(_FakeAsyncClient):
        async def post(self, url, json=None):
            raise ConnectionError("ml-clustering unreachable")

    monkeypatch.setattr(report_service.httpx, "AsyncClient", _BoomAsyncClient)

    assert await report_service._fetch_cluster_labels(["Kafka"]) == {}


@pytest.mark.asyncio
async def test_top_mentioned_techs_counts_only_mentions_inside_the_date_range(monkeypatch):
    # Regression: published_date is a plain string in Neo4j, so a Cypher `date($start)` comparison
    # always evaluates to NULL and silently drops every row — counting must happen here instead.
    rows = [
        {"tech_name": "Kafka", "published_date": "2026-05-01"},
        {"tech_name": "Kafka", "published_date": "2026-06-01"},
        {"tech_name": "Kafka", "published_date": "2026-08-01"},  # outside range
        {"tech_name": "Redis", "published_date": "2026-05-15"},
    ]

    async def fake_run_query(cypher, params=None):
        return rows

    monkeypatch.setattr(report_service, "run_query", fake_run_query)

    result = await report_service._top_mentioned_techs("2026-04-01", "2026-07-31")

    assert result == [
        {"tech_name": "Kafka", "mention_count": 2},
        {"tech_name": "Redis", "mention_count": 1},
    ]


@pytest.mark.asyncio
async def test_top_mentioned_techs_handles_mixed_date_formats(monkeypatch):
    rows = [
        {"tech_name": "Kafka", "published_date": "2026-05-01"},
        {"tech_name": "Kafka", "published_date": "20/05/2026"},
        {"tech_name": "Kafka", "published_date": "not-a-date"},
    ]

    async def fake_run_query(cypher, params=None):
        return rows

    monkeypatch.setattr(report_service, "run_query", fake_run_query)

    result = await report_service._top_mentioned_techs("2026-04-01", "2026-07-31")

    assert result == [{"tech_name": "Kafka", "mention_count": 2}]


@pytest.mark.asyncio
async def test_top_mentioned_techs_respects_the_limit(monkeypatch):
    async def fake_run_query(cypher, params=None):
        return [{"tech_name": f"tech{i}", "published_date": "2026-05-01"} for i in range(5)]

    monkeypatch.setattr(report_service, "run_query", fake_run_query)

    result = await report_service._top_mentioned_techs("2026-04-01", "2026-07-31", limit=2)

    assert len(result) == 2


@pytest.mark.asyncio
async def test_top_mentioned_techs_returns_empty_list_when_the_query_fails(monkeypatch):
    async def fake_run_query(cypher, params=None):
        raise ConnectionError("neo4j unreachable")

    monkeypatch.setattr(report_service, "run_query", fake_run_query)

    assert await report_service._top_mentioned_techs("2026-04-01", "2026-07-31") == []
