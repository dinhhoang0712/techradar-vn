"""
Gold — Technology Category Backfill (chạy 1 lần, không nằm trong lịch nightly)

Phân loại category cho TOÀN BỘ :Technology node đã tồn tại trong Neo4j từ trước — khác với
MVP đi tới trong tech_dedup.py (chỉ phân loại tên MỚI phát hiện, rơi vào nhánh "unresolved").
Tên đã có sẵn trong dp_tech_alias_map từ trước không bao giờ rơi vào nhánh đó, nên cần quét
lại toàn bộ 1 lần riêng.

Tái dùng nguyên _call_llm/_parse_llm_categories/_save_categories của tech_dedup.py — cùng
prompt phân loại, chỉ khác input là TOÀN BỘ tên thay vì chỉ tên chưa biết. Idempotent: bỏ qua
tên đã có trong dp_tech_category, an toàn để chạy lại nếu bị gián đoạn giữa chừng.

Cách chạy (từ thư mục data-platform/):
    python3 -m gold.tech_category_backfill
"""

from __future__ import annotations

from common.db import get_neo4j_driver, get_pg_conn
from config import Settings, get_settings
from loguru import logger

from gold.tech_dedup import _call_llm, _fetch_technology_names, _parse_llm_categories, _save_categories

_BATCH_SIZE = 50


def _load_categorized_names(conn) -> set[str]:
    with conn.cursor() as cur:
        cur.execute("SELECT canonical_name FROM dp_tech_category")
        rows = cur.fetchall()
    return {r["canonical_name"] for r in rows}


def _chunk(items: list[str], size: int) -> list[list[str]]:
    return [items[i : i + size] for i in range(0, len(items), size)]


def run(settings: Settings) -> dict:
    logger.info("Category Backfill: starting...")
    pg_conn = get_pg_conn(settings)
    driver = get_neo4j_driver(settings)

    try:
        all_names = _fetch_technology_names(driver)
        already_categorized = _load_categorized_names(pg_conn)
        pending = [n for n in all_names if n not in already_categorized]

        logger.info(
            "Category Backfill: {} Technology name(s) tổng, {} đã có category, {} cần phân loại",
            len(all_names),
            len(already_categorized),
            len(pending),
        )

        total_categorized = 0
        for batch in _chunk(pending, _BATCH_SIZE):
            raw = _call_llm(batch, settings)
            categories = _parse_llm_categories(raw)
            category_by_name = {c["name"]: c["category"] for c in categories if c.get("name") and c.get("category")}
            _save_categories(pg_conn, category_by_name)
            total_categorized += len(category_by_name)
            logger.info("Category Backfill: đã phân loại {} tên (batch {} tên)", total_categorized, len(batch))

        logger.info("Category Backfill: done — {} tên đã phân loại", total_categorized)
        return {"categorized": total_categorized, "already_categorized": len(already_categorized)}

    finally:
        driver.close()
        pg_conn.close()


if __name__ == "__main__":
    from common.logger import configure_logging

    configure_logging()
    run(get_settings())
