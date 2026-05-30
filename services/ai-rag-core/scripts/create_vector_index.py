"""
Chặng 0 — Tạo vector index trên Neo4j cho node Article.

Chạy SAU khi embed_articles.py đã xong và có ít nhất 1 Article có property `embedding`.

Cách chạy (từ thư mục src/ai-rag-core/):
    python -m scripts.create_vector_index
"""

import asyncio
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent.parent))

from neo4j import AsyncGraphDatabase
from app.config import get_settings


async def main() -> None:
    settings = get_settings()

    uri = settings.active_neo4j_uri
    auth = (settings.active_neo4j_username, settings.active_neo4j_password)

    if uri.startswith("neo4j+s://"):
        uri = uri.replace("neo4j+s://", "neo4j+ssc://", 1)

    index_name = settings.neo4j_vector_index  # "article_embedding_index"
    dim = settings.embedding_dim              # 768

    print(f"Kết nối Neo4j: {uri}")
    print(f"Sẽ tạo index: {index_name}  (dim={dim}, similarity=cosine)")

    driver = AsyncGraphDatabase.driver(uri, auth=auth)

    try:
        async with driver.session() as session:

            # Kiểm tra đã tồn tại chưa
            result = await session.run(
                "SHOW INDEXES WHERE type = 'VECTOR' AND name = $name",
                {"name": index_name},
            )
            existing = await result.data()

            if existing:
                state = existing[0].get("state", "?")
                print(f"Index '{index_name}' đã tồn tại (state={state}). Không cần tạo lại.")
                return

            # Tạo index mới
            print("Đang tạo vector index...")
            await session.run(
                f"""
                CREATE VECTOR INDEX {index_name}
                FOR (a:Article) ON (a.embedding)
                OPTIONS {{
                    indexConfig: {{
                        `vector.dimensions`: {dim},
                        `vector.similarity_function`: 'cosine'
                    }}
                }}
                """
            )
            print("Lệnh tạo index đã gửi. Đang chờ index build xong...")

            # Poll cho đến khi ONLINE
            for attempt in range(30):
                await asyncio.sleep(3)
                result = await session.run(
                    "SHOW INDEXES WHERE type = 'VECTOR' AND name = $name",
                    {"name": index_name},
                )
                rows = await result.data()
                if rows:
                    state = rows[0].get("state", "?")
                    print(f"  [{attempt + 1}] state = {state}")
                    if state == "ONLINE":
                        break

            # Test nhanh
            print("\nTest query vector search...")
            result = await session.run(
                """
                MATCH (a:Article)
                WHERE a.embedding IS NOT NULL
                RETURN elementId(a) AS eid, a.embedding AS emb
                LIMIT 1
                """
            )
            sample = await result.single()
            if sample:
                emb = sample["emb"]
                result2 = await session.run(
                    """
                    CALL db.index.vector.queryNodes($index_name, 5, $embedding)
                    YIELD node, score
                    RETURN node.title AS title, score
                    """,
                    {"index_name": index_name, "embedding": emb},
                )
                rows = await result2.data()
                print(f"Vector search trả về {len(rows)} kết quả:")
                for row in rows:
                    print(f"  score={row['score']:.4f} | {row['title']}")
            else:
                print("Không có Article nào có embedding — chạy embed_articles.py trước.")

    finally:
        await driver.close()


if __name__ == "__main__":
    asyncio.run(main())
