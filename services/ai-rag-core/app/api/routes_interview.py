from fastapi import APIRouter, Depends

from app.api.schemas import InterviewRequest, InterviewResponse
from app.api.security import require_internal_auth
from app.services import interview_service

router = APIRouter(
    prefix="/interview",
    tags=["interview"],
    dependencies=[Depends(require_internal_auth)],
)


@router.post("", response_model=InterviewResponse)
async def interview_turn(req: InterviewRequest) -> InterviewResponse:
    """
    Một lượt phỏng vấn thử: client gửi kèm toàn bộ lịch sử câu hỏi-trả lời đã có
    (`history`); rỗng nghĩa là bắt đầu buổi mới. Stateless — không lưu phiên.
    """
    return await interview_service.handle(req)
