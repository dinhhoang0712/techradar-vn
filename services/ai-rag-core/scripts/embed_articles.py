"""
Chặng 0 — Embed toàn bộ Article trong Neo4j và ghi lại property `embedding`.

Cách chạy (từ thư mục src/ai-rag-core/):
    python -m scripts.embed_articles

Hoặc chạy trực tiếp:
    python scripts/embed_articles.py

Yêu cầu: file .env phải có NEO4J_URI + NEO4J_PASSWORD (hoặc bật USE_LOCAL_NEO4J=true).
"""

import asyncio
import sys
import time
from pathlib import Path

# Cho phép import app.* khi chạy trực tiếp
sys.path.insert(0, str(Path(__file__).parent.parent))

from neo4j import AsyncGraphDatabase
from app.config import get_settings
from app.core.embedder import embed_batch

BATCH_SIZE = 32  # Số Article xử lý mỗi lần (giới hạn bởi RAM model)
WRITE_BATCH = 100  # Số Article ghi lên Neo4j mỗi lần


def build_text(title: str | None, content: str | None) -> str:
    """Ghép title + content thành chuỗi để embed."""
    parts = []
    if title:
        parts.append(title.strip())
    if content:
        parts.append(content.strip())
    return " ".join(parts) if parts else ""


async def fetch_articles(driver) -> list[dict]:
    """Lấy toàn bộ Article chưa có embedding."""
    async with driver.session() as session:
        result = await session.run(
            """
            MATCH (a:Article)
            WHERE a.embedding IS NULL
            RETURN elementId(a) AS eid, a.title AS title, a.content AS content
            """
        )
        return await result.data()


async def write_embeddings(driver, rows: list[dict]) -> None:
    """Ghi embedding vào Neo4j theo batch."""
    async with driver.session() as session:
        await session.run(
            """
            UNWIND $rows AS row
            MATCH (a:Article)
            WHERE elementId(a) = row.eid
            SET a.embedding = row.embedding
            """,
            {"rows": rows},
        )


async def main() -> None:
    settings = get_settings()

    uri = settings.active_neo4j_uri
    auth = (settings.active_neo4j_username, settings.active_neo4j_password)

    # neo4j+ssc:// để bỏ qua verify SSL khi dùng AuraDB trên macOS
    if uri.startswith("neo4j+s://"):
        uri = uri.replace("neo4j+s://", "neo4j+ssc://", 1)

    print(f"Kết nối Neo4j: {uri}")
    driver = AsyncGraphDatabase.driver(uri, auth=auth)

    try:
        print("Đang tải danh sách Article chưa embed...")
        articles = await fetch_articles(driver)
        total = len(articles)

        if total == 0:
            print("Không có Article nào cần embed. Kiểm tra lại DB hoặc chạy lại sau khi clear embedding.")
            return

        print(f"Tìm thấy {total} Article cần embed.")

        texts = [build_text(a["title"], a["content"]) for a in articles]
        eids = [a["eid"] for a in articles]

        skipped = sum(1 for t in texts if not t.strip())
        if skipped:
            print(f"Cảnh báo: {skipped} Article có title + content rỗng — sẽ embed chuỗi rỗng.")

        print(f"\nBắt đầu embed {total} Article bằng {settings.embedding_model}...")
        t0 = time.time()

        vectors = embed_batch(texts, is_query=False)

        elapsed = time.time() - t0
        print(f"Embed xong trong {elapsed:.1f}s ({total / elapsed:.1f} article/s)")

        # Ghi lên Neo4j theo từng batch
        rows = [{"eid": eid, "embedding": vec} for eid, vec in zip(eids, vectors)]

        print(f"\nGhi {total} embedding lên Neo4j (batch={WRITE_BATCH})...")
        t1 = time.time()

        for i in range(0, total, WRITE_BATCH):
            chunk = rows[i : i + WRITE_BATCH]
            await write_embeddings(driver, chunk)
            done = min(i + WRITE_BATCH, total)
            print(f"  [{done}/{total}] đã ghi")

        elapsed_write = time.time() - t1
        print(f"Ghi xong trong {elapsed_write:.1f}s")

        # Verify
        async with driver.session() as session:
            result = await session.run(
                "MATCH (a:Article) RETURN count(a) AS total, count(a.embedding) AS embedded"
            )
            rec = await result.single()
            print(f"\nKiểm tra: {rec['embedded']} / {rec['total']} Article đã có embedding trong DB.")

    finally:
        await driver.close()


if __name__ == "__main__":
    asyncio.run(main())
