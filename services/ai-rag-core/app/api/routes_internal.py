"""
Internal endpoints — gọi từ Spring gateway (apps/backend), không expose ra client.

POST /internal/ai/llm-summary
  Sinh tóm tắt so sánh 2 công nghệ dựa trên các chỉ số tăng trưởng / việc làm /
  bài viết. Khớp contract của backend PythonAiClient: request snake_case, response
  trả về {"summary": <text>}.

POST /internal/ai/moderation-suggestion
  Gợi ý hành động kiểm duyệt (REMOVE/DISMISS) cho một báo cáo nội dung, kèm lý do
  và độ tin cậy. Chỉ là gợi ý — admin vẫn phải bấm áp dụng thủ công ở phía backend.
"""
import json
import logging
import re

from fastapi import APIRouter, Depends, HTTPException

from app.api.schemas import (
    LlmSummaryRequest, LlmSummaryResponse,
    ModerationSuggestionRequest, ModerationSuggestionResponse,
)
from app.api.security import require_internal_auth
from app.core.generator import generate

logger = logging.getLogger("ai-rag-core.moderation")

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


def _build_moderation_messages(req: ModerationSuggestionRequest) -> list[dict]:
    system = (
        "Bạn là chuyên gia kiểm duyệt nội dung mạng xã hội tại Việt Nam. "
        "Đánh giá nội dung bị báo cáo có thực sự vi phạm chính sách cộng đồng "
        "(quấy rối, thù ghét, spam, nội dung khiêu dâm/bạo lực, thông tin sai lệch nghiêm trọng) hay không. "
        "Chỉ trả về JSON hợp lệ, không thêm chữ nào khác: "
        '{"action": "REMOVE" hoặc "DISMISS", "reason": "<1-2 câu giải thích bằng tiếng Việt>", "confidence": <số 0.0-1.0>}'
    )
    user = (
        f"Loại nội dung: {req.target_type}\n"
        f"Nội dung bị báo cáo: \"{req.target_content}\"\n"
        f"Lý do người dùng báo cáo: \"{req.report_reason}\"\n\n"
        "Nội dung này có vi phạm chính sách và nên bị xoá (REMOVE), hay báo cáo nên được bỏ qua (DISMISS)?"
    )
    return [
        {"role": "system", "content": system},
        {"role": "user", "content": user},
    ]


@router.post("/moderation-suggestion", response_model=ModerationSuggestionResponse)
async def moderation_suggestion(req: ModerationSuggestionRequest) -> ModerationSuggestionResponse:
    """Gợi ý hành động kiểm duyệt ({"action", "reason", "confidence"}).

    Chỉ raise 503 khi chính lời gọi LLM thất bại; nếu LLM trả về JSON không hợp lệ
    hoặc thiếu field, fallback về DISMISS/confidence=0.0 thay vì raise — admin luôn
    có thể xem xét thủ công, một gợi ý tồi không nên chặn cả luồng kiểm duyệt.
    """
    try:
        raw = await generate(_build_moderation_messages(req))
    except Exception as e:
        raise HTTPException(status_code=503, detail=f"Moderation suggestion failed: {e}")

    action = "DISMISS"
    reason = "Không thể phân tích phản hồi AI, cần admin xem xét thủ công."
    confidence = 0.0
    try:
        match = re.search(r"\{.*\}", raw, re.DOTALL)
        if match:
            parsed = json.loads(match.group())
            if parsed.get("action") in ("REMOVE", "DISMISS"):
                action = parsed["action"]
                reason = str(parsed.get("reason", reason))
                confidence = max(0.0, min(1.0, float(parsed.get("confidence", 0.0))))
    except Exception as e:
        logger.warning("Failed to parse moderation suggestion JSON: %s", e)

    return ModerationSuggestionResponse(action=action, reason=reason, confidence=confidence)
