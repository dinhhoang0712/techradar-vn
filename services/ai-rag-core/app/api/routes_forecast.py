from fastapi import APIRouter, Depends

from app.api.schemas import ForecastRequest, ForecastResponse
from app.api.security import require_internal_auth
from app.services import forecast_service

router = APIRouter(
    prefix="/forecast",
    tags=["forecast"],
    dependencies=[Depends(require_internal_auth)],
)


@router.post("", response_model=ForecastResponse)
async def forecast(req: ForecastRequest) -> ForecastResponse:
    """
    Dự báo xu hướng công nghệ dựa trên time-series analytics + graph signals + LLM.

    - `technology`: tên công nghệ cần dự báo (VD: "React", "Kubernetes")
    - `horizon_months`: số tháng dự báo (1-24, mặc định 6)
    """
    return await forecast_service.handle(req)
