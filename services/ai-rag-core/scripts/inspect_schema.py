"""
Inspect schema của tất cả node labels trong Neo4j.

Cách chạy (từ thư mục src/ai-rag-core/):
    python -m scripts.inspect_schema
"""

import asyncio
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent.parent))

from neo4j import AsyncGraphDatabase
from app.config import get_settings


async def fetch_labels(driver) -> list[str]:
    async with driver.session() as session:
        result = await session.run("CALL db.labels() YIELD label RETURN label ORDER BY label")
        rows = await result.data()
        return [r["label"] for r in rows]


async def fetch_properties(driver, label: str) -> dict:
    """
    Lấy tất cả property keys của một label và thống kê:
    - sample values (3 record đầu)
    - số node có / không có property đó
    """
    async with driver.session() as session:
        # Lấy tất cả property keys xuất hiện trong label
        result = await session.run(
            f"""
            MATCH (n:{label})
            WITH keys(n) AS ks
            UNWIND ks AS k
            RETURN DISTINCT k ORDER BY k
            """
        )
        rows = await result.data()
        prop_keys = [r["k"] for r in rows]

    if not prop_keys:
        return {}

    props = {}
    for key in prop_keys:
        async with driver.session() as session:
            result = await session.run(
                f"""
                MATCH (n:{label})
                WITH
                    count(n)                         AS total,
                    count(n.`{key}`)                 AS has_value,
                    collect(n.`{key}`)[..3]          AS samples
                RETURN total, has_value, samples
                """
            )
            row = await result.single()
            props[key] = {
                "total":     row["total"],
                "has_value": row["has_value"],
                "missing":   row["total"] - row["has_value"],
                "samples":   row["samples"],
            }

    return props


async def fetch_relationships(driver) -> list[dict]:
    async with driver.session() as session:
        result = await session.run(
            """
            CALL db.relationshipTypes() YIELD relationshipType AS rel
            RETURN rel ORDER BY rel
            """
        )
        rows = await result.data()
        return [r["rel"] for r in rows]


async def fetch_rel_stats(driver, rel_type: str) -> dict:
    async with driver.session() as session:
        result = await session.run(
            f"""
            MATCH (a)-[r:{rel_type}]->(b)
            RETURN
                labels(a)[0] AS from_label,
                labels(b)[0] AS to_label,
                count(r)     AS cnt
            ORDER BY cnt DESC
            LIMIT 5
            """
        )
        return await result.data()


def _fmt_sample(val) -> str:
    s = str(val)
    return s[:60] + "…" if len(s) > 60 else s


def _print_section(title: str) -> None:
    print(f"\n{'═' * 60}")
    print(f"  {title}")
    print(f"{'═' * 60}")


async def main() -> None:
    settings = get_settings()
    uri = settings.active_neo4j_uri
    if uri.startswith("neo4j+s://"):
        uri = uri.replace("neo4j+s://", "neo4j+ssc://", 1)

    print(f"Kết nối: {uri}")
    driver = AsyncGraphDatabase.driver(
        uri, auth=(settings.active_neo4j_username, settings.active_neo4j_password)
    )

    try:
        labels = await fetch_labels(driver)
        rels   = await fetch_relationships(driver)

        _print_section("NODE LABELS")
        print(f"  {labels}")

        _print_section("RELATIONSHIP TYPES")
        print(f"  {rels}")

        for label in labels:
            _print_section(f"NODE: {label}")
            props = await fetch_properties(driver, label)
            if not props:
                print("  (không có property nào)")
                continue

            col_w = max(len(k) for k in props) + 2
            print(f"  {'Property':<{col_w}} {'Count':>7}  {'Missing':>7}  Samples")
            print(f"  {'-' * col_w} {'-------':>7}  {'-------':>7}  -------")
            for key, info in props.items():
                samples_str = " | ".join(_fmt_sample(s) for s in info["samples"]) or "—"
                print(
                    f"  {key:<{col_w}} {info['has_value']:>7}  {info['missing']:>7}  {samples_str}"
                )

        _print_section("RELATIONSHIP STATS")
        for rel in rels:
            stats = await fetch_rel_stats(driver, rel)
            for row in stats:
                print(f"  ({row['from_label']})-[:{rel}]->({row['to_label']})  ×{row['cnt']}")

    finally:
        await driver.close()


if __name__ == "__main__":
    asyncio.run(main())
