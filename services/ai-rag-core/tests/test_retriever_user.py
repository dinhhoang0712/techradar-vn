import pytest
from unittest.mock import AsyncMock, MagicMock
from app.core.retriever_user import get_user_context, build_user_block # type: ignore # noqa

@pytest.mark.asyncio
async def test_get_user_context_success(monkeypatch):
    """Kiểm tra lấy context user thành công."""
    mock_row = {
        "user_id": "uuid-123",
        "full_name": "Test User",
        "job_role": "Dev",
        "technologies": ["Python"],
        "location": "Hanoi",
        "bio": "Bio"
    }
    
    mock_result = MagicMock()
    mock_result.mappings.return_value.first.return_value = mock_row
    
    mock_session = AsyncMock()
    mock_session.execute.return_value = mock_result
    
    # Mock factory as an async context manager
    mock_factory = MagicMock()
    mock_factory.return_value.__aenter__.return_value = mock_session
    mock_factory.return_value.__aexit__.return_value = AsyncMock()
    
    monkeypatch.setattr("app.core.retriever_user.get_session_factory", lambda: mock_factory)
    
    result = await get_user_context("uuid-123")
    
    assert result["full_name"] == "Test User"
    assert "Python" in result["technologies"]

@pytest.mark.asyncio
async def test_get_user_context_db_error(monkeypatch):
    """Kiểm tra khi DB lỗi, get_user_context phải return None thay vì raise exception."""
    mock_factory = MagicMock()
    # Giả lập lỗi khi mở session
    mock_factory.return_value.__aenter__.side_effect = Exception("DB down")
    mock_factory.return_value.__aexit__ = AsyncMock()
    
    monkeypatch.setattr("app.core.retriever_user.get_session_factory", lambda: mock_factory)
    
    result = await get_user_context("uuid-123")
    assert result is None

def test_build_user_block_full():
    """Kiểm tra format đầy đủ thông tin user."""
    ctx = {
        "job_role": "AI Engineer",
        "technologies": ["PyTorch", "NLP"],
        "location": "HCM",
        "bio": "Exp"
    }
    block = build_user_block(ctx)
    assert "- Vai trò: AI Engineer" in block
    assert "PyTorch, NLP" in block

def test_build_user_block_empty():
    """Kiểm tra khi context không có thông tin gì hữu ích."""
    ctx = {"user_id": "123"}
    block = build_user_block(ctx)
    assert block == ""
