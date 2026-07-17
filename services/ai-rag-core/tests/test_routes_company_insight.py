"""
Tests cho POST /company-insight.

Chạy từ thư mục service (để `app` import được):
    cd services/ai-rag-core
    pytest tests/test_routes_company_insight.py -v

Mock `generate` và `run_query` nên KHÔNG cần LLM hay Neo4j thật.
"""
import pytest
from fastapi import FastAPI
from fastapi.testclient import TestClient

from app.api import routes_company_insight
from app.services import company_insight_service


@pytest.fixture
def client(monkeypatch):
    async def fake_generate(messages):
        fake_generate.last_messages = messages
        if "Trích" in messages[0]["content"]:
            return '["Đang tuyển mạnh backend", "Stack hiện đại"]'
        return "Acme đang mở rộng đội ngũ kỹ thuật với stack hiện đại."

    monkeypatch.setattr(company_insight_service, "generate", fake_generate)

    app = FastAPI()
    app.include_router(routes_company_insight.router)
    return TestClient(app), fake_generate


def _mock_context(monkeypatch, rows):
    async def fake_run_query(cypher, params=None):
        return rows

    monkeypatch.setattr(company_insight_service, "run_query", fake_run_query)


def test_returns_summary_and_highlights_when_company_has_job_data(client, monkeypatch):
    c, fake = client
    _mock_context(monkeypatch, [{
        "name": "Acme Corp", "location": "Hà Nội", "industry": "Fintech",
        "size": "100-500", "tech_stack": ["Java", "Spring"], "job_count": 5,
    }])

    res = c.post("/company-insight", json={"company_name": "Acme Corp"})

    assert res.status_code == 200
    body = res.json()
    assert body["company_name"] == "Acme Corp"
    assert "Acme" in body["summary"]
    assert body["highlights"] == ["Đang tuyển mạnh backend", "Stack hiện đại"]

    user_prompt = fake.last_messages[1]["content"]
    assert "Acme Corp" in user_prompt
    assert "Fintech" in user_prompt
    assert "Java, Spring" in user_prompt


def test_returns_fallback_message_when_company_has_no_job_data(client, monkeypatch):
    c, _ = client
    _mock_context(monkeypatch, [])

    res = c.post("/company-insight", json={"company_name": "Unknown Co"})

    assert res.status_code == 200
    body = res.json()
    assert body["highlights"] == []
    assert "Unknown Co" in body["summary"]


def test_missing_required_field_422(client):
    c, _ = client
    res = c.post("/company-insight", json={})
    assert res.status_code == 422


def test_neo4j_lookup_failure_falls_back_to_the_no_data_message(client, monkeypatch):
    # _fetch_company_context swallows Neo4j errors and returns None (same as summarize_service's
    # _fetch_articles) — the Java aiproxy layer is what turns a genuine LLM/network failure into a
    # 503 for the frontend (see CompanyInsightControllerTest), not this route.
    c, _ = client

    async def boom(cypher, params=None):
        raise RuntimeError("neo4j down")

    monkeypatch.setattr(company_insight_service, "run_query", boom)
    res = c.post("/company-insight", json={"company_name": "Acme Corp"})

    assert res.status_code == 200
    body = res.json()
    assert body["highlights"] == []
    assert "Acme Corp" in body["summary"]
