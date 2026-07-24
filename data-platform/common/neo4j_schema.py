"""
Neo4j — Uniqueness constraints, tạo idempotent lúc data-platform khởi động.

Thay thế phần schema-setup của `knowledge-graph/utils/schema_define.py` (đã xoá khỏi
repo — xem docs/DATABASE.md §4): từ đó không còn script nào tạo/duy trì constraint,
khiến `MERGE` race giữa nhiều writer (Java realtime + Python batch, cùng ghi 1 Neo4j)
có thể sinh node trùng lặp khi 2 transaction cùng lúc đọc "chưa tồn tại" trước khi bên
nào commit. Đã xác nhận thực tế trên môi trường sống: `TypeScript`/`Laravel`/`PHP` có
2-3 node trùng y hệt tên trước khi dedup thủ công qua `gold/tech_dedup.py`.

Khoá unique theo ĐÚNG property mà MỌI writer hiện tại dùng để MERGE (xem
apps/backend `Neo4jExtractionWriter.java` + `gold/neo4j_article_sync.py` /
`neo4j_job_sync.py`):
  - Article/Company/Job: `id` (hash/slug xác định trước, KHÔNG phải tên hiển thị)
  - Technology/Skill/Location: `name` (chưa có id xác định trước — mọi writer đều MERGE
    theo tên)

`CREATE CONSTRAINT ... IF NOT EXISTS` an toàn gọi lại mỗi lần data-platform restart.
Neo4j sẽ từ chối tạo constraint nếu dữ liệu hiện có đã vi phạm uniqueness — vì vậy
PHẢI dedup trước khi bật (xem `gold/tech_dedup.py`), không tự dedup-rồi-tạo-constraint
trong cùng 1 lần gọi ở đây, để lỗi dedup không bị che giấu bởi vẻ "đã tạo constraint
thành công".
"""

from __future__ import annotations

from loguru import logger

_CONSTRAINTS = [
    ("article_id_unique", "Article", "id"),
    ("company_id_unique", "Company", "id"),
    ("job_id_unique", "Job", "id"),
    ("technology_name_unique", "Technology", "name"),
    ("skill_name_unique", "Skill", "name"),
    ("location_name_unique", "Location", "name"),
]


def ensure_constraints(driver) -> None:
    with driver.session() as session:
        for constraint_name, label, prop in _CONSTRAINTS:
            try:
                session.run(
                    f"CREATE CONSTRAINT {constraint_name} IF NOT EXISTS "
                    f"FOR (n:{label}) REQUIRE n.{prop} IS UNIQUE"
                )
                logger.info("Neo4j constraint '{}' ({}.{}) OK", constraint_name, label, prop)
            except Exception as exc:
                # Không raise — dữ liệu hiện có có thể còn vi phạm uniqueness (cần
                # dedup trước, xem gold/tech_dedup.py), và không nên chặn toàn bộ
                # service khởi động vì 1 constraint lỗi.
                logger.warning("Neo4j constraint '{}' chưa tạo được: {}", constraint_name, exc)
