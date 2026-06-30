from fastapi import APIRouter, Depends
from sqlalchemy.ext.asyncio import AsyncSession

from app.api.schemas import CareerRequest, CareerResponse
from app.api.security import require_internal_auth
from app.db.postgres_client import get_session
from app.services import career_service

router = APIRouter(
    prefix="/career",
    tags=["career"],
    dependencies=[Depends(require_internal_auth)],
)


@router.post("", response_model=CareerResponse)
async def career_advice(
    req: CareerRequest,
    db: AsyncSession = Depends(get_session),
) -> CareerResponse:
    """
    Tư vấn lộ trình career dựa trên skill hiện tại và vai trò mục tiêu.
    Kết hợp Neo4j skill graph + job demand analytics + LLM roadmap.
    """
    return await career_service.handle(req, db)
