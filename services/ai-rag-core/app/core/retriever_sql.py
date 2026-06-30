import logging
from contextlib import asynccontextmanager

from sqlalchemy import text

from app.db.postgres_client import get_session_factory

logger = logging.getLogger("ai-rag-core.retriever_sql")


async def sql_analytics_search(tech_names: list[str], months: int = 6) -> list[dict]:
    """
    Đọc tech_analytics từ PostgreSQL cho danh sách tech.
    Bảng này do Gold ETL (data-platform/gold/pg_etl.py) maintain — Python chỉ đọc.

    Schema: tech_analytics(id, technology_name, month, job_count,
                            article_count, growth_rate, mom_growth, yoy_growth)
    """
    if not tech_names:
        return []

    factory = get_session_factory()
    async with factory() as session:
        try:
            result = await session.execute(
                text("""
                    SELECT
                        technology_name,
                        month,
                        job_count,
                        article_count,
                        growth_rate,
                        mom_growth,
                        yoy_growth
                    FROM tech_analytics
                    WHERE technology_name = ANY(:names)
                      AND month >= (CURRENT_DATE - CAST(:months || ' months' AS INTERVAL))
                    ORDER BY technology_name, month DESC
                """),
                {"names": tech_names, "months": months},
            )
            return [dict(row._mapping) for row in result]
        except Exception as e:
            logger.warning("sql_analytics_search failed: %s", e)
            return []


async def sql_trending_techs(limit: int = 20) -> list[dict]:
    """
    Top tech tăng trưởng nhất trong tháng gần nhất.
    Dùng cho Forecast và Report services.
    """
    factory = get_session_factory()
    async with factory() as session:
        try:
            result = await session.execute(
                text("""
                    SELECT
                        technology_name,
                        job_count,
                        article_count,
                        growth_rate,
                        mom_growth,
                        yoy_growth,
                        month
                    FROM tech_analytics
                    WHERE month = (SELECT MAX(month) FROM tech_analytics)
                    ORDER BY COALESCE(mom_growth, 0) DESC NULLS LAST
                    LIMIT :limit
                """),
                {"limit": limit},
            )
            return [dict(row._mapping) for row in result]
        except Exception as e:
            logger.warning("sql_trending_techs failed: %s", e)
            return []


async def sql_tech_timeseries(tech_name: str, months: int = 12) -> list[dict]:
    """
    Time-series data cho 1 tech — dùng cho Forecast service.
    Trả về theo thứ tự tăng dần (oldest → newest) để dễ phân tích trend.
    """
    factory = get_session_factory()
    async with factory() as session:
        try:
            result = await session.execute(
                text("""
                    SELECT
                        technology_name,
                        month,
                        job_count,
                        article_count,
                        growth_rate,
                        mom_growth,
                        yoy_growth
                    FROM tech_analytics
                    WHERE technology_name = :name
                      AND month >= (CURRENT_DATE - CAST(:months || ' months' AS INTERVAL))
                    ORDER BY month ASC
                """),
                {"name": tech_name, "months": months},
            )
            return [dict(row._mapping) for row in result]
        except Exception as e:
            logger.warning("sql_tech_timeseries failed for %s: %s", tech_name, e)
            return []
