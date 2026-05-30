import pytest
from unittest.mock import AsyncMock, MagicMock
from app.core.generator import generate # type: ignore # noqa
from app.core.generator_stream import generate_stream # type: ignore # noqa

@pytest.mark.asyncio
async def test_generate_success(monkeypatch):
    """Kiểm tra gọi Gemini thành công."""
    mock_llm = MagicMock()
    mock_llm.ainvoke = AsyncMock(return_value=MagicMock(content="OK"))
    monkeypatch.setattr("app.core.generator.get_llm", lambda: mock_llm)
    assert await generate([{"role": "user", "content": "Hi"}]) == "OK"

@pytest.mark.asyncio
async def test_generate_retry_then_success(monkeypatch):
    """Kiểm tra logic retry khi gặp lỗi 503 rồi thành công."""
    mock_llm = MagicMock()
    mock_llm.ainvoke = AsyncMock(side_effect=[
        Exception("503 Service Unavailable"),
        MagicMock(content="Fixed")
    ])
    monkeypatch.setattr("app.core.generator.get_llm", lambda: mock_llm)
    monkeypatch.setattr("app.core.generator._RETRY_DELAY", 0.01)
    assert await generate([{"role": "user", "content": "Hi"}]) == "Fixed"

@pytest.mark.asyncio
async def test_generate_max_retries_reached(monkeypatch):
    """Kiểm tra khi retry hết số lần vẫn lỗi -> raise RuntimeError."""
    mock_llm = MagicMock()
    mock_llm.ainvoke = AsyncMock(side_effect=Exception("503 Server Busy"))
    monkeypatch.setattr("app.core.generator.get_llm", lambda: mock_llm)
    monkeypatch.setattr("app.core.generator._RETRY_DELAY", 0.01)
    
    with pytest.raises(RuntimeError) as exc:
        await generate([{"role": "user", "content": "Hi"}])
    assert "lỗi:" in str(exc.value)
    assert "503 Server Busy" in str(exc.value)

@pytest.mark.asyncio
async def test_generate_stream_success(monkeypatch):
    """Kiểm tra stream thành công từ Gemini."""
    mock_llm = MagicMock()
    async def mock_astream(messages):
        yield MagicMock(content="A")
        yield MagicMock(content="B")
    mock_llm.astream = mock_astream
    monkeypatch.setattr("app.core.generator_stream.get_llm", lambda: mock_llm)
    
    chunks = []
    async for chunk in generate_stream([{"role": "user", "content": "Hi"}]):
        chunks.append(chunk)
    assert chunks == ["A", "B"]
