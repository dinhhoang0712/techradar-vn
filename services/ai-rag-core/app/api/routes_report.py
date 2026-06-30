from fastapi import APIRouter, Depends

from app.api.schemas import ReportRequest, ReportResponse
from app.api.security import require_internal_auth
from app.services import report_service

router = APIRouter(
    prefix="/report",
    tags=["report"],
    dependencies=[Depends(require_internal_auth)],
)


@router.post("", response_model=ReportResponse)
async def generate_report(req: ReportRequest) -> ReportResponse:
    """
    Tạo báo cáo xu hướng công nghệ tổng hợp theo kỳ.

    - `period`: kỳ báo cáo (VD: "2024-Q4", "2024-12", "2024")
    - `top_n`: số công nghệ top (5-30, mặc định 10)
    - `format`: "markdown" | "json"
    """
    return await report_service.handle(req)
