from typing import Any

from app.config import get_settings

_driver: Any | None = None


async def get_driver() -> Any:
    global _driver
    if _driver is None:
        from neo4j import AsyncGraphDatabase

        settings = get_settings()
        uri = settings.active_neo4j_uri
        # AuraDB trên macOS cần bypass SSL verify
        if uri.startswith("neo4j+s://"):
            uri = uri.replace("neo4j+s://", "neo4j+ssc://", 1)
        _driver = AsyncGraphDatabase.driver(
            uri,
            auth=(settings.active_neo4j_username, settings.active_neo4j_password),
            max_connection_pool_size=50,
        )
    return _driver


async def close_driver() -> None:
    global _driver
    if _driver is not None:
        await _driver.close()
        _driver = None


async def run_query(cypher: str, params: dict | None = None) -> list[dict]:
    driver = await get_driver()
    async with driver.session() as session:
        result = await session.run(cypher, params or {})
        return await result.data()


async def ping() -> bool:
    try:
        await run_query("RETURN 1")
        return True
    except Exception:
        return False
