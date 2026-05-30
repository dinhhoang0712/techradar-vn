import pytest
from fastapi.testclient import TestClient
from unittest.mock import AsyncMock, MagicMock
from contextlib import asynccontextmanager
from app.main import app # type: ignore # noqa
from app.db.postgres_client import get_session # type: ignore # noqa
from app.api.routes_embed import get_settings as get_embed_settings # type: ignore # noqa

# Mock lifespan and dependencies
@pytest.fixture
def client(monkeypatch):
    # Mock lifespan trực tiếp trên instance app bằng dummy async context manager
    @asynccontextmanager
    async def dummy_lifespan(app_inst):
        yield
    app.router.lifespan_context = dummy_lifespan
    
    # Mock Neo4j ping
    mock_ping = AsyncMock(return_value=True)
    monkeypatch.setattr("app.api.routes_health.ping", mock_ping)
    return TestClient(app)

def test_health_check(client):
    """Kiểm tra endpoint health."""
    response = client.get("/health")
    assert response.status_code == 200
    assert response.json()["status"] == "ok"

def test_embed_status_endpoint(client):
    """Kiểm tra endpoint status của embed job."""
    response = client.get("/embed/status")
    assert response.status_code == 200
    assert "status" in response.json()

def test_chat_stream_endpoint(client, monkeypatch):
    """Kiểm tra POST /chat/stream trả về EventSourceResponse."""
    async def mock_gen(req, db):
        yield {"event": "token", "data": "T"}
        yield {"event": "done", "data": {"answer": "T"}}
        
    monkeypatch.setattr("app.api.routes_chat.handle_chat_stream", mock_gen)
    
    response = client.post("/chat/stream", json={"query": "Stream me"})
    assert response.status_code == 200
    assert "text/event-stream" in response.headers["content-type"]

def test_list_messages_endpoint(client, monkeypatch):
    """Kiểm tra GET /chat/session/{id}/messages."""
    mock_result = MagicMock()
    mock_result.scalars.return_value.all.return_value = []
    mock_session = AsyncMock()
    mock_session.execute.return_value = mock_result
    
    app.dependency_overrides[get_session] = lambda: mock_session
    
    sess_id = "550e8400-e29b-41d4-a716-446655440000"
    response = client.get(f"/chat/session/{sess_id}/messages")
    
    assert response.status_code == 200
    assert isinstance(response.json(), list)
    app.dependency_overrides.clear()

def test_trigger_embed_auth(client, monkeypatch):
    """Kiểm tra xác thực tại POST /embed/trigger."""
    mock_settings = MagicMock()
    mock_settings.embed_secret = "secret123"
    monkeypatch.setattr("app.api.routes_embed.get_settings", lambda: mock_settings)
    
    # Sai secret -> 401
    response = client.post("/embed/trigger", headers={"X-Embed-Secret": "wrong"})
    assert response.status_code == 401
    
    # Đúng secret -> 200
    monkeypatch.setattr("app.api.routes_embed._embed_running", False)
    response = client.post("/embed/trigger", headers={"X-Embed-Secret": "secret123"})
    assert response.status_code == 200
    assert response.json()["status"] == "started"
