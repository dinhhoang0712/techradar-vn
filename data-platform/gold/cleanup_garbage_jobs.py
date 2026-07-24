"""
Gold — Cleanup Garbage Jobs (chạy 1 lần, không nằm trong lịch nightly)

Dọn các :Job node là dữ liệu rác từ crawl bị chặn/lỗi (không phải tin tuyển dụng thật) — xem
`gold/kg_health_audit.py`'s `_check_garbage_jobs` cho điều kiện phát hiện chính xác. Root-cause
đã được chặn tại nguồn ở `silver/processor.py` (`_is_blocked_page_job`) — script này chỉ dọn phần
ĐÃ LỌT QUA trước khi có fix đó (48 node xác nhận trên dữ liệu thật tại thời điểm viết).

2 bước:
1. Xoá node rác khỏi Neo4j (`DETACH DELETE` theo `j.id` — khớp chính xác node đã phát hiện qua
   cùng 1 điều kiện với KG Health Audit, không xoá nhầm node khác dù trùng tiêu đề).
2. Đánh dấu `dp_processed_jobs.status = 'invalid'` cho cùng `id` — để `neo4j_job_sync.py`
   (`WHERE status = 'processed'`) không đồng bộ lại các dòng này ở lần chạy sau.

Idempotent: chạy lại khi không còn Job rác nào sẽ không làm gì (0 xoá, 0 đánh dấu).

Cách chạy (từ thư mục data-platform/):
    python3 -m gold.cleanup_garbage_jobs
"""

from __future__ import annotations

from common.db import get_neo4j_driver, get_pg_conn
from config import Settings, get_settings
from gold.kg_health_audit import _check_garbage_jobs
from loguru import logger

_DELETE_JOBS_QUERY = "MATCH (j:Job) WHERE j.id IN $ids DETACH DELETE j"
_MARK_INVALID_QUERY = "UPDATE dp_processed_jobs SET status = 'invalid' WHERE id = ANY(%s)"


def run(settings: Settings) -> dict:
    logger.info("Cleanup Garbage Jobs: starting...")
    driver = get_neo4j_driver(settings)
    pg_conn = get_pg_conn(settings)

    try:
        garbage = _check_garbage_jobs(driver)
        ids = [row["id"] for row in garbage if row.get("id")]

        if not ids:
            logger.info("Cleanup Garbage Jobs: không tìm thấy Job rác nào, không có gì để dọn")
            return {"deleted_from_neo4j": 0, "marked_invalid_in_postgres": 0}

        with driver.session() as session:
            session.run(_DELETE_JOBS_QUERY, ids=ids)

        with pg_conn.cursor() as cur:
            cur.execute(_MARK_INVALID_QUERY, (ids,))
            marked = cur.rowcount
        pg_conn.commit()

        logger.info(
            "Cleanup Garbage Jobs: đã xoá {} node khỏi Neo4j, đánh dấu invalid {} dòng ở Postgres",
            len(ids),
            marked,
        )
        return {"deleted_from_neo4j": len(ids), "marked_invalid_in_postgres": marked}

    finally:
        driver.close()
        pg_conn.close()


if __name__ == "__main__":
    from common.logger import configure_logging

    configure_logging()
    result = run(get_settings())
    print(result)
