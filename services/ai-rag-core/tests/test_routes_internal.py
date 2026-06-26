"""
Tests cho POST /internal/ai/llm-summary (endpoint gọi từ Spring gateway).

Chạy từ thư mục service (để `app` import được):
    cd services/ai-rag-core
    pytest tests/test_routes_internal.py -v

Mock `generate` nên KHÔNG cần API key LLM hay mạng. Chỉ cần fastapi/pydantic.
"""
import pytest
from fastapi import FastAPI
from fastapi.testclient import TestClient

from app.api import routes_internal


@pytest.fixture
def client(monkeypatch):
    async def fake_generate(messages):
        fake_generate.last_messages = messages
        return "  Python có đà tăng trưởng tốt hơn Go.  "  # cố tình thừa khoảng trắng

    monkeypatch.setattr(routes_internal, "generate", fake_generate)

    app = FastAPI()
    app.include_router(routes_internal.router)
    return TestClient(app), fake_generate


def test_path_and_response_shape(client):
    c, fake = client
    payload = {
        "tech1": "Python", "tech2": "Go",
        "growth_rate_1": 12.5, "growth_rate_2": -3.0,
        "job_count_1": 120, "job_count_2": None,
        "article_count_1": 0, "article_count_2": 8,
    }
    res = c.post("/internal/ai/llm-summary", json=payload)

    assert res.status_code == 200
    body = res.json()
    # Đúng contract backend: response chỉ có key "summary"
    assert set(body.keys()) == {"summary"}
    assert body["summary"] == "Python có đà tăng trưởng tốt hơn Go."  # đã .strip()

    # Prompt phản ánh đúng số liệu đầu vào (snake_case)
    user = fake.last_messages[1]["content"]
    assert "Python" in user and "Go" in user
    assert "+12.5%" in user and "-3.0%" in user
    assert "120" in user
    assert user.count("không có dữ liệu") == 1   # đúng 1 field thiếu (job_count_2)


def test_minimal_required_fields_ok(client):
    c, _ = client
    res = c.post("/internal/ai/llm-summary", json={"tech1": "Rust", "tech2": "C++"})
    assert res.status_code == 200
    assert "summary" in res.json()


def test_missing_required_field_422(client):
    c, _ = client
    res = c.post("/internal/ai/llm-summary", json={"tech1": "Rust"})  # thiếu tech2
    assert res.status_code == 422


def test_llm_error_returns_503(client, monkeypatch):
    c, _ = client

    async def boom(messages):
        raise RuntimeError("LLM down")

    monkeypatch.setattr(routes_internal, "generate", boom)
    res = c.post("/internal/ai/llm-summary", json={"tech1": "A", "tech2": "B"})
    assert res.status_code == 503
