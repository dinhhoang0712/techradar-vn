"""
Internal endpoints — gọi từ Spring gateway (apps/backend), không expose ra client.

POST /internal/ai/llm-summary
  Sinh tóm tắt so sánh 2 công nghệ dựa trên các chỉ số tăng trưởng / việc làm /
  bài viết. Khớp contract của backend PythonAiClient: request snake_case, response
  trả về {"summary": <text>}.
"""
from fastapi import APIRouter, Depends, HTTPException

from app.api.schemas import LlmSummaryRequest, LlmSummaryResponse
from app.api.security import require_internal_auth
from app.core.generator import generate

router = APIRouter(prefix="/internal/ai", tags=["internal"], dependencies=[Depends(require_internal_auth)])


def _fmt_rate(value: float | None) -> str:
    return f"{value:+.1f}%" if value is not None else "không có dữ liệu"


def _fmt_count(value: int | None) -> str:
    return str(value) if value is not None else "không có dữ liệu"


def _build_messages(req: LlmSummaryRequest) -> list[dict]:
    system = (
        "Bạn là chuyên gia phân tích xu hướng công nghệ IT tại Việt Nam. "
        "Viết một đoạn so sánh ngắn gọn (3-5 câu), khách quan, bằng tiếng Việt, "
        "dựa hoàn toàn trên số liệu được cung cấp. Tuyệt đối không bịa thêm số liệu."
    )
    user = (
        "So sánh hai công nghệ dựa trên dữ liệu thị trường:\n\n"
        f"1) {req.tech1}\n"
        f"   - Tốc độ tăng trưởng: {_fmt_rate(req.growth_rate_1)}\n"
        f"   - Số tin tuyển dụng: {_fmt_count(req.job_count_1)}\n"
        f"   - Số bài viết: {_fmt_count(req.article_count_1)}\n\n"
        f"2) {req.tech2}\n"
        f"   - Tốc độ tăng trưởng: {_fmt_rate(req.growth_rate_2)}\n"
        f"   - Số tin tuyển dụng: {_fmt_count(req.job_count_2)}\n"
        f"   - Số bài viết: {_fmt_count(req.article_count_2)}\n\n"
        "Nhận xét công nghệ nào đang có đà phát triển và nhu cầu tốt hơn, kèm lý do."
    )
    return [
        {"role": "system", "content": system},
        {"role": "user", "content": user},
    ]


@router.post("/llm-summary", response_model=LlmSummaryResponse)
async def llm_summary(req: LlmSummaryRequest) -> LlmSummaryResponse:
    """Trả về tóm tắt so sánh 2 công nghệ ({"summary": ...})."""
    try:
        summary = await generate(_build_messages(req))
    except Exception as e:
        raise HTTPException(status_code=503, detail=f"LLM summary failed: {e}")
    return LlmSummaryResponse(summary=summary.strip())
