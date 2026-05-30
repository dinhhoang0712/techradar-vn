import asyncio
from fastapi import APIRouter, BackgroundTasks, Header, HTTPException
from pydantic import BaseModel

from app.config import get_settings

router = APIRouter(prefix="/embed", tags=["embed"])

_embed_running = False  # lock đơn giản, tránh chạy 2 job song song


class EmbedStatus(BaseModel):
    status: str
    message: str


async def _run_embed_job() -> None:
    """Embed toàn bộ Article chưa có embedding rồi tạo/verify vector index."""
    global _embed_running
    try:
        from neo4j import AsyncGraphDatabase
        from app.core.embedder import embed_batch
        from app.db.neo4j_client import run_query

        settings = get_settings()
        uri = settings.active_neo4j_uri
        if uri.startswith("neo4j+s://"):
            uri = uri.replace("neo4j+s://", "neo4j+ssc://", 1)

        driver = AsyncGraphDatabase.driver(
            uri, auth=(settings.active_neo4j_username, settings.active_neo4j_password)
        )

        # 1. Lấy Article chưa embed
        async with driver.session() as session:
            result = await session.run(
                "MATCH (a:Article) WHERE a.embedding IS NULL "
                "RETURN elementId(a) AS eid, a.title AS title, a.content AS content"
            )
            articles = await result.data()

        if not articles:
            print("[embed-job] Không có Article mới cần embed.")
            await driver.close()
            return

        print(f"[embed-job] Tìm thấy {len(articles)} Article cần embed...")

        # 2. Embed
        texts = [f"{a.get('title') or ''} {a.get('content') or ''}".strip() for a in articles]
        vectors = await asyncio.get_event_loop().run_in_executor(
            None, lambda: embed_batch(texts, is_query=False)
        )

        # 3. Ghi lên Neo4j
        rows = [{"eid": a["eid"], "embedding": vec} for a, vec in zip(articles, vectors)]
        WRITE_BATCH = 100
        async with driver.session() as session:
            for i in range(0, len(rows), WRITE_BATCH):
                await session.run(
                    "UNWIND $rows AS row "
                    "MATCH (a:Article) WHERE elementId(a) = row.eid "
                    "SET a.embedding = row.embedding",
                    {"rows": rows[i : i + WRITE_BATCH]},
                )
        await driver.close()
        print(f"[embed-job] Đã embed và ghi {len(articles)} Article lên Neo4j.")

    except Exception as e:
        print(f"[embed-job] Lỗi: {e}")
    finally:
        _embed_running = False


@router.post("/trigger", response_model=EmbedStatus)
async def trigger_embed(
    background_tasks: BackgroundTasks,
    x_embed_secret: str = Header(..., description="Secret key để xác thực crawler"),
) -> EmbedStatus:
    """
    Crawler gọi endpoint này sau mỗi lần crawl xong để embed các Article mới.
    Yêu cầu header: X-Embed-Secret = EMBED_SECRET trong .env
    """
    global _embed_running

    settings = get_settings()
    if x_embed_secret != settings.embed_secret:
        raise HTTPException(status_code=401, detail="Invalid embed secret")

    if _embed_running:
        return EmbedStatus(status="skipped", message="Embed job đang chạy, bỏ qua request này.")

    _embed_running = True
    background_tasks.add_task(_run_embed_job)

    return EmbedStatus(status="started", message="Embed job đã bắt đầu chạy nền.")


@router.get("/status", response_model=EmbedStatus)
async def embed_status() -> EmbedStatus:
    """Kiểm tra embed job có đang chạy không."""
    if _embed_running:
        return EmbedStatus(status="running", message="Embed job đang chạy.")
    return EmbedStatus(status="idle", message="Không có job nào đang chạy.")
