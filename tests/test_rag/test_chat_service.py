import pytest
import uuid
from unittest.mock import AsyncMock, MagicMock
from app.models.user import User  # type: ignore # noqa # Cần để SQLAlchemy load mapper
from app.services.chat_service import handle_chat, handle_chat_stream # type: ignore # noqa
from app.api.schemas import ChatRequest # type: ignore # noqa

@pytest.mark.asyncio
async def test_handle_chat_new_session(monkeypatch, mock_db):
    """Kiểm tra tạo session mới và lưu message khi không truyền session_id."""
    mock_db.execute.return_value.scalar_one_or_none.return_value = None
    monkeypatch.setattr("app.services.chat_service.answer", AsyncMock(return_value={
        "answer": "Test answer", "sources": [], "entities": [], "job_titles": []
    }))
    
    req = ChatRequest(query="Hello", user_id=uuid.uuid4())
    response = await handle_chat(req, mock_db)
    
    assert response.answer == "Test answer"
    assert response.session_id is not None
    assert mock_db.add.call_count == 3
    assert mock_db.commit.call_count == 1

@pytest.mark.asyncio
async def test_handle_chat_reuse_session(monkeypatch, mock_db):
    """Kiểm tra reuse session hiện có."""
    session_id = uuid.uuid4()
    mock_session_obj = MagicMock(id=session_id)
    mock_db.execute.return_value.scalar_one_or_none.return_value = mock_session_obj
    
    monkeypatch.setattr("app.services.chat_service.answer", AsyncMock(return_value={"answer": "OK"}))
    
    req = ChatRequest(query="Hi", session_id=session_id)
    response = await handle_chat(req, mock_db)
    
    assert response.session_id == session_id
    assert mock_db.add.call_count == 2 # user msg + assistant msg

@pytest.mark.asyncio
async def test_handle_chat_stream_full_flow(mock_db, monkeypatch):
    """Kiểm tra handle_chat_stream yield đủ token/done và lưu đúng content vào DB."""
    async def mock_answer_stream(query, user_id):
        yield {"event": "token", "data": "Chào"}
        yield {"event": "token", "data": " bạn"}
        yield {"event": "done", "data": {"answer": "Chào bạn", "sources": []}}
        
    monkeypatch.setattr("app.services.chat_service.answer_stream", mock_answer_stream)
    
    req = ChatRequest(query="Hi")
    events = []
    async for ev in handle_chat_stream(req, mock_db):
        events.append(ev)
        
    assert events[0] == {"event": "token", "data": "Chào"}
    assert events[1] == {"event": "token", "data": " bạn"}
    assert events[2]["event"] == "done"
    assert events[2]["data"]["answer"] == "Chào bạn"
    assert events[2]["data"]["session_id"] is not None
    assert events[2]["data"]["query"] == "Hi"
    
    # Kiểm tra assistant message được lưu với content đầy đủ "Chào bạn"
    # Lấy đối tượng cuối cùng được add vào DB
    # mock_db.add.call_args_list[0] là session, [1] là user msg, [2] là assistant msg
    assistant_msg = mock_db.add.call_args_list[2][0][0]
    assert assistant_msg.role == "assistant"
    assert assistant_msg.content == "Chào bạn"

@pytest.mark.asyncio
async def test_handle_chat_session_not_found(mock_db, monkeypatch):
    """Kiểm tra khi truyền session_id không tồn tại -> phải tạo session mới."""
    mock_db.execute.return_value.scalar_one_or_none.return_value = None
    monkeypatch.setattr("app.services.chat_service.answer", AsyncMock(return_value={"answer": "OK"}))
    
    req = ChatRequest(query="Hi", session_id=uuid.uuid4())
    response = await handle_chat(req, mock_db)
    
    assert response.answer == "OK"
    assert mock_db.add.call_count == 3 # Phải gọi tạo session mới
