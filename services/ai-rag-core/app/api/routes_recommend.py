from fastapi import APIRouter, Depends
from sqlalchemy.ext.asyncio import AsyncSession

from app.api.schemas import RecommendRequest, RecommendResponse
from app.api.security import require_internal_auth
from app.db.postgres_client import get_session
from app.services import recommend_service

router = APIRouter(
    prefix="/recommend",
    tags=["recommend"],
    dependencies=[Depends(require_internal_auth)],
)


@router.post("", response_model=RecommendResponse)
async def recommend(
    req: RecommendRequest,
    db: AsyncSession = Depends(get_session),
) -> RecommendResponse:
    """
    Gợi ý công nghệ / kỹ năng phù hợp với user.

    - Lấy tech user đang dùng từ `user_profile.preferences_json` nếu có `user_id`.
    - Override bằng `current_techs` nếu muốn recommend theo tech list tùy chọn.
    - Kết hợp Neo4j graph co-occurrence + PostgreSQL trend analytics + LLM explain.
    """
    return await recommend_service.handle(req, db)
