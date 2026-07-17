from fastapi import APIRouter, Depends

from app.api.schemas import CompanyInsightRequest, CompanyInsightResponse
from app.api.security import require_internal_auth
from app.services import company_insight_service

router = APIRouter(
    prefix="/company-insight",
    tags=["company-insight"],
    dependencies=[Depends(require_internal_auth)],
)


@router.post("", response_model=CompanyInsightResponse)
async def company_insight(req: CompanyInsightRequest) -> CompanyInsightResponse:
    """
    Nhận định AI ngắn gọn về 1 công ty, dựa trên tech stack/quy mô tuyển dụng suy ra từ Neo4j.

    - `company_name`: tên công ty (khớp không phân biệt hoa/thường)
    """
    return await company_insight_service.handle(req)
