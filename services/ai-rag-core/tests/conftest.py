import os
import sys
from pathlib import Path
from unittest.mock import AsyncMock, MagicMock

import pytest

# Thêm thư mục service (services/ai-rag-core) vào path để import được app
rag_core_path = str(Path(__file__).parent.parent)
if rag_core_path not in sys.path:
    sys.path.insert(0, rag_core_path)

# Phải set TRƯỚC khi bất kỳ test module nào được pytest collect (import) — test_routes.py làm
# `from app.main import app` ở module level, việc này gọi get_settings() ngay lúc import, TRƯỚC
# khi fixture autouse mock_env_vars bên dưới kịp chạy (fixture chỉ áp dụng lúc chạy test, không
# áp dụng lúc collection). Thiếu bước set ở module-level này thì collection tự crash với
# pydantic ValidationError (neo4j_uri/neo4j_password required, không có default).
os.environ.setdefault("NEO4J_URI", "bolt://localhost:7687")
os.environ.setdefault("NEO4J_USERNAME", "neo4j")
os.environ.setdefault("NEO4J_PASSWORD", "password")
os.environ.setdefault("GEMINI_API_KEY", "fake_key")
os.environ.setdefault("POSTGRES_HOST", "localhost")
os.environ.setdefault("POSTGRES_DB", "test_db")


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
    db.add = MagicMock()  # SQLAlchemy add() is sync
    mock_result = MagicMock()
    mock_result.scalar_one_or_none.return_value = None
    mock_result.mappings.return_value.first.return_value = None
    db.execute.return_value = mock_result
    return db
