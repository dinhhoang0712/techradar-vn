from fastapi import APIRouter, Depends

from app.api.schemas import SummarizeRequest, SummarizeResponse
from app.api.security import require_internal_auth
from app.services import summarize_service

router = APIRouter(
    prefix="/summarize",
    tags=["summarize"],
    dependencies=[Depends(require_internal_auth)],
)


@router.post("", response_model=SummarizeResponse)
async def summarize(req: SummarizeRequest) -> SummarizeResponse:
    """
    Tóm tắt xu hướng công nghệ từ bài viết trong khoảng thời gian.

    - `tech_name`: tên công nghệ (VD: "Kubernetes")
    - `period`: kỳ thời gian (VD: "2024-Q4", "2024-12", None = 3 tháng gần nhất)
    - `format`: "paragraph" | "bullet" | "structured"
    """
    return await summarize_service.handle(req)
