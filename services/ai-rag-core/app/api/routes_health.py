from fastapi import APIRouter

from app.api.schemas import HealthResponse
from app.db.neo4j_client import ping

router = APIRouter(tags=["health"])


@router.get("/health", response_model=HealthResponse)
async def health() -> HealthResponse:
    """Kiểm tra trạng thái service và kết nối Neo4j."""
    neo4j_ok = await ping()
    return HealthResponse(
        status="ok" if neo4j_ok else "degraded",
        neo4j=neo4j_ok,
    )
