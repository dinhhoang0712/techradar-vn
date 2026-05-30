"""
Migrate toàn bộ data từ AuraDB → Neo4j local.

Cách chạy (từ thư mục src/ai-rag-core/):
    python3 -m scripts.migrate_aura_to_local

Thời gian ước tính: 2–5 phút (tùy mạng và máy).
Chạy lại an toàn — script xóa sạch local DB trước khi import.
"""

import asyncio
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent.parent))

from neo4j import AsyncGraphDatabase

# ── Kết nối AuraDB (nguồn) ───────────────────────────────────────────────────
AURA_URI  = "neo4j+ssc://8db1137b.databases.neo4j.io"
AURA_AUTH = ("8db1137b", "dVrIjC7xWkGclw29TGuqcUxGxcms3s7_dPI0jsHI4-M")

# ── Kết nối local (đích) ─────────────────────────────────────────────────────
LOCAL_URI  = "bolt://localhost:7687"
LOCAL_AUTH = ("neo4j", "localpassword")

# Số record mỗi lần đọc/ghi
BATCH = 200

LABELS = ["Article", "Technology", "Company", "Job", "Skill", "Person"]
REL_TYPES = ["USES", "MENTIONS", "REQUIRES", "HIRES_FOR", "RELATED_TO", "IS_TECHNOLOGY"]


# ─────────────────────────────────────────────────────────────────────────────
# Helpers
# ─────────────────────────────────────────────────────────────────────────────

async def count(session, cypher: str, params: dict = {}) -> int:
    r = await session.run(cypher, params)
    rec = await r.single()
    return rec[0]


async def run(session, cypher: str, params: dict = {}):
    r = await session.run(cypher, params)
    return await r.data()


# ─────────────────────────────────────────────────────────────────────────────
# Bước 1 — Xóa sạch local DB
# ─────────────────────────────────────────────────────────────────────────────

async def clear_local(local: AsyncGraphDatabase):
    print("Xóa sạch local DB...")
    async with local.session() as s:
        await s.run("MATCH ()-[r]->() DELETE r")
        await s.run("MATCH (n) DELETE n")
        total = await count(s, "MATCH (n) RETURN count(n)")
        print(f"  Local DB còn {total} nodes sau khi xóa.")


# ─────────────────────────────────────────────────────────────────────────────
# Bước 2 — Export nodes từ AuraDB, import vào local
# ─────────────────────────────────────────────────────────────────────────────

async def migrate_nodes(aura: AsyncGraphDatabase, local: AsyncGraphDatabase):
    print("\nMigrate nodes...")
    total_migrated = 0

    for label in LABELS:
        async with aura.session() as src:
            # Lấy tổng số
            total = await count(src, f"MATCH (n:{label}) RETURN count(n)")

        imported = 0
        skip = 0

        while skip < total:
            async with aura.session() as src:
                rows = await run(src, f"""
                    MATCH (n:{label})
                    RETURN elementId(n) AS _eid, properties(n) AS props
                    SKIP {skip} LIMIT {BATCH}
                """)

            if not rows:
                break

            # Thêm _eid vào props để dùng khi tạo relationship
            batch_data = []
            for row in rows:
                props = dict(row["props"])
                props["_eid"] = row["_eid"]
                batch_data.append(props)

            async with local.session() as dst:
                await dst.run(f"""
                    UNWIND $rows AS props
                    CREATE (n:{label})
                    SET n = props
                """, {"rows": batch_data})

            imported += len(rows)
            skip += BATCH
            print(f"  {label}: {imported}/{total}")

        total_migrated += imported

    print(f"Tổng nodes đã migrate: {total_migrated}")


# ─────────────────────────────────────────────────────────────────────────────
# Bước 3 — Export relationships từ AuraDB, import vào local
# ─────────────────────────────────────────────────────────────────────────────

async def migrate_relationships(aura: AsyncGraphDatabase, local: AsyncGraphDatabase):
    print("\nMigrate relationships...")
    total_migrated = 0

    for rel_type in REL_TYPES:
        async with aura.session() as src:
            total = await count(src, f"""
                MATCH ()-[r:{rel_type}]->() RETURN count(r)
            """)

        if total == 0:
            print(f"  {rel_type}: 0 — bỏ qua")
            continue

        imported = 0
        skip = 0

        while skip < total:
            async with aura.session() as src:
                rows = await run(src, f"""
                    MATCH (a)-[r:{rel_type}]->(b)
                    RETURN elementId(a) AS src_eid,
                           elementId(b) AS tgt_eid,
                           properties(r) AS props
                    SKIP {skip} LIMIT {BATCH}
                """)

            if not rows:
                break

            async with local.session() as dst:
                await dst.run(f"""
                    UNWIND $rows AS row
                    MATCH (a {{_eid: row.src_eid}})
                    MATCH (b {{_eid: row.tgt_eid}})
                    CREATE (a)-[r:{rel_type}]->(b)
                    SET r = row.props
                """, {"rows": rows})

            imported += len(rows)
            skip += BATCH
            print(f"  {rel_type}: {imported}/{total}")

        total_migrated += imported

    print(f"Tổng relationships đã migrate: {total_migrated}")


# ─────────────────────────────────────────────────────────────────────────────
# Bước 4 — Xóa property _eid tạm thời
# ─────────────────────────────────────────────────────────────────────────────

async def cleanup_eid(local: AsyncGraphDatabase):
    print("\nXóa property _eid tạm thời...")
    async with local.session() as s:
        await s.run("MATCH (n) WHERE n._eid IS NOT NULL REMOVE n._eid")
    print("  Xong.")


# ─────────────────────────────────────────────────────────────────────────────
# Bước 5 — Verify
# ─────────────────────────────────────────────────────────────────────────────

async def verify(local: AsyncGraphDatabase):
    print("\nKiểm tra local DB sau migrate:")
    async with local.session() as s:
        for label in LABELS:
            cnt = await count(s, f"MATCH (n:{label}) RETURN count(n)")
            print(f"  {label}: {cnt}")

        r = await s.run("""
            MATCH ()-[r]->()
            RETURN type(r) AS t, count(r) AS cnt
            ORDER BY cnt DESC
        """)
        rows = await r.data()
        print("  Relationships:")
        for row in rows:
            print(f"    {row['t']}: {row['cnt']}")


# ─────────────────────────────────────────────────────────────────────────────
# Main
# ─────────────────────────────────────────────────────────────────────────────

async def main():
    t0 = time.time()

    aura  = AsyncGraphDatabase.driver(AURA_URI,  auth=AURA_AUTH)
    local = AsyncGraphDatabase.driver(LOCAL_URI, auth=LOCAL_AUTH)

    try:
        print("=== Migrate AuraDB → Local Neo4j ===\n")

        await clear_local(local)
        await migrate_nodes(aura, local)
        await migrate_relationships(aura, local)
        await cleanup_eid(local)
        await verify(local)

        elapsed = time.time() - t0
        print(f"\nHoàn thành trong {elapsed:.1f}s")

    finally:
        await aura.close()
        await local.close()


if __name__ == "__main__":
    asyncio.run(main())
