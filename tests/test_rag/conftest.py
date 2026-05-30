import os
import sys
import pytest
from unittest.mock import MagicMock, AsyncMock
from pathlib import Path

# Thêm src/ai-rag-core vào path để import được app
rag_core_path = str(Path(__file__).parent.parent.parent / "src" / "ai-rag-core")
if rag_core_path not in sys.path:
    sys.path.insert(0, rag_core_path)

@pytest.fixture(autouse=True)
def mock_env_vars(monkeypatch):
    """Giả lập các biến môi trường cần thiết."""
    monkeypatch.setenv("NEO4J_URI", "bolt://localhost:7687")
    monkeypatch.setenv("NEO4J_USERNAME", "neo4j")
    monkeypatch.setenv("NEO4J_PASSWORD", "password")
    monkeypatch.setenv("GEMINI_API_KEY", "fake_key")
    monkeypatch.setenv("POSTGRES_HOST", "localhost")
    monkeypatch.setenv("POSTGRES_DB", "test_db")

@pytest.fixture
def mock_llm():
    """Mock Gemini LLM."""
    mock = MagicMock()
    mock.ainvoke.return_value.content = '{"technologies": ["Python"], "job_titles": ["Developer"]}'
    return mock

@pytest.fixture
def mock_neo4j_session():
    """Mock Neo4j Session."""
    mock = MagicMock()
    mock.__aenter__.return_value = mock
    return mock
@pytest.fixture
def mock_db():
    """Mock SQLAlchemy AsyncSession với các phương thức thực thi cơ bản."""
    db = AsyncMock()
    db.add = MagicMock() # SQLAlchemy add() is sync
    mock_result = MagicMock()
    mock_result.scalar_one_or_none.return_value = None
    mock_result.mappings.return_value.first.return_value = None
    db.execute.return_value = mock_result
    return db
