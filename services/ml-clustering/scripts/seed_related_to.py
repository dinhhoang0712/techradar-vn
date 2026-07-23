"""
Seed các quan hệ RELATED_TO đã curate thủ công (conf/seed_data/related_to_seed.json)
vào Neo4j — chạy được trên BẤT KỲ máy nào sau khi `git clone`, không phụ thuộc
Neo4j instance nào (khớp theo Technology.name, không theo elementId — elementId
chỉ đúng trên đúng 1 Neo4j database, không portable giữa các máy/instance).

Đây là lý do RELATED_TO cần seed lại thủ công trên mỗi máy: quan hệ này là
GROUND TRUTH do con người curate (domain knowledge), không phải thứ crawler
tự sinh ra được — nên phải "cài đặt" lại mỗi khi có Neo4j mới (máy khác, Neo4j
Docker mới tinh, AuraDB project mới...).

Idempotent: dùng MERGE, chạy lại nhiều lần không tạo trùng.
Không raise nếu 1 tech trong cặp chưa tồn tại trong DB (máy mới có thể chưa
crawl đủ) — chỉ log cảnh báo, bỏ qua cặp đó, tiếp tục.

CLI:
    cd services/ml-clustering
    python -m scripts.seed_related_to
    python -m scripts.seed_related_to --seed-file conf/seed_data/related_to_seed.json
"""

from __future__ import annotations

import json
from pathlib import Path

import typer
from loguru import logger

app = typer.Typer(add_completion=False, help="Seed RELATED_TO ground-truth vào Neo4j")

MODULE_ROOT = Path(__file__).resolve().parents[1]
DEFAULT_SEED_FILE = MODULE_ROOT / "conf" / "seed_data" / "related_to_seed.json"


@app.command()
def main(
    seed_file: str = typer.Option(str(DEFAULT_SEED_FILE), help="Đường dẫn file JSON chứa cặp RELATED_TO"),
) -> None:
    from src.data import neo4j_loader as loader

    seed_path = Path(seed_file)
    if not seed_path.exists():
        logger.error("Không tìm thấy seed file: {}", seed_path)
        raise typer.Exit(code=1)

    data = json.loads(seed_path.read_text(encoding="utf-8"))
    pairs: list[list[str]] = data["pairs"]
    logger.info("Đọc {} cặp từ {}", len(pairs), seed_path)

    try:
        loader.run_query("RETURN 1")
    except Exception as exc:
        logger.error("Kết nối Neo4j thất bại: {}", exc)
        raise typer.Exit(code=1) from exc

    existing_names = {r["name"] for r in loader.run_query("MATCH (t:Technology) RETURN t.name AS name")}
    logger.info("Neo4j hiện có {} Technology node.", len(existing_names))

    created = 0
    skipped: list[tuple[str, str]] = []
    for a, b in pairs:
        if a not in existing_names or b not in existing_names:
            skipped.append((a, b))
            continue
        loader.run_query(
            """
            MATCH (x:Technology {name: $a}), (y:Technology {name: $b})
            MERGE (x)-[:RELATED_TO]->(y)
            """,
            {"a": a, "b": b},
        )
        created += 1

    total = loader.run_query("MATCH (:Technology)-[:RELATED_TO]->(:Technology) RETURN count(*) AS c")[0]["c"]
    loader.close_driver()

    print(f"\n{'=' * 55}")
    print("  Seed RELATED_TO hoàn tất")
    print(f"{'=' * 55}")
    print(f"  Áp dụng      : {created}/{len(pairs)} cặp")
    print(f"  Bỏ qua       : {len(skipped)} cặp (thiếu tech trong DB máy này)")
    if skipped:
        sample = ", ".join(f"{a}-{b}" for a, b in skipped[:10])
        more = f" (+{len(skipped) - 10} nữa)" if len(skipped) > 10 else ""
        print(f"    {sample}{more}")
    print(f"  Tổng RELATED_TO edges trong DB hiện tại: {total}")
    print(f"{'=' * 55}\n")


if __name__ == "__main__":
    app()
