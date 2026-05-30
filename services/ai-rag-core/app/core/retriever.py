import asyncio

from app.db.neo4j_client import run_query
from app.core.embedder import embed_query
from app.config import get_settings


async def vector_search(query: str, top_k: int = 10) -> list[dict]:
    """
    Embed câu hỏi → tìm Article gần nhất bằng Neo4j vector index.
    Trả về list dict: {id, title, content, source, published_date, sentiment_score, score}

    Schema thực tế (AuraDB 25/04/2026):
    - Dùng `content` (không phải `description`)
    - Dùng `source` (không phải `url`) — đa số = "Unknown"
    - Không có `is_relevant`, `article_type` — không filter theo 2 field này
    - id = elementId() của Neo4j vì node không có property `id`
    """
    settings = get_settings()
    loop = asyncio.get_event_loop()
    query_vector = await loop.run_in_executor(None, embed_query, query)

    cypher = """
    CALL db.index.vector.queryNodes($index_name, $top_k, $embedding)
    YIELD node AS article, score
    RETURN
        elementId(article)      AS id,
        article.title           AS title,
        article.content         AS content,
        article.source          AS source,
        article.published_date  AS published_date,
        article.sentiment_score AS sentiment_score,
        score
    ORDER BY score DESC
    """

    return await run_query(
        cypher,
        {
            "index_name": settings.neo4j_vector_index,
            "top_k": top_k,
            "embedding": query_vector,
        },
    )
