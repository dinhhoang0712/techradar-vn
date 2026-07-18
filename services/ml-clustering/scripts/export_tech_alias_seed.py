"""
Export bảng `dp_tech_alias_map` (Postgres, nguồn sự thật dùng chung giữa
`apps/backend` và `data-platform`) ra file JSON portable — check-in vào git để
`ml-clustering` có thể merge vào `TECH_ALIAS_MAP` (src/features/tech_aliases.py)
mà KHÔNG cần tự kết nối Postgres lúc train (giữ service này độc lập/portable,
chỉ phụ thuộc Neo4j — cùng tinh thần với `seed_related_to.py`).

Đây là snapshot tĩnh, không đồng bộ realtime: chạy lại script này (thủ công,
hoặc thêm vào quy trình release) mỗi khi `dp_tech_alias_map` có alias mới đáng
kể (vd sau khi `tech_dedup.py` tự học thêm alias qua LLM) để cập nhật seed.

CLI:
    cd services/ml-clustering
    python -m scripts.export_tech_alias_seed
    python -m scripts.export_tech_alias_seed --postgres-dsn postgresql://postgres:postgres@localhost:5432/techradar
"""

from __future__ import annotations

import json
import os
from pathlib import Path

import psycopg2
import psycopg2.extras
import typer
from loguru import logger

app = typer.Typer(add_completion=False, help="Export dp_tech_alias_map ra seed file cho ml-clustering")

MODULE_ROOT = Path(__file__).resolve().parents[1]
DEFAULT_OUTPUT_FILE = MODULE_ROOT / "conf" / "seed_data" / "tech_alias_seed.json"
DEFAULT_DSN = "postgresql://postgres:postgres@localhost:5432/techradar"


@app.command()
def main(
    postgres_dsn: str = typer.Option(
        os.environ.get("POSTGRES_DSN", DEFAULT_DSN),
        help="Postgres DSN — mặc định đọc từ env POSTGRES_DSN, fallback localhost dev",
    ),
    output_file: str = typer.Option(str(DEFAULT_OUTPUT_FILE), help="Đường dẫn file JSON output"),
) -> None:
    conn = psycopg2.connect(postgres_dsn, cursor_factory=psycopg2.extras.RealDictCursor)
    try:
        with conn.cursor() as cur:
            cur.execute(
                "SELECT alias_normalized, canonical_name FROM dp_tech_alias_map "
                "ORDER BY alias_normalized"
            )
            rows = cur.fetchall()
    finally:
        conn.close()

    aliases = {row["alias_normalized"]: row["canonical_name"] for row in rows}
    logger.info("Đọc {} alias từ dp_tech_alias_map.", len(aliases))

    output_path = Path(output_file)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(
        json.dumps({"aliases": aliases}, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )

    print(f"\n{'=' * 55}")
    print("  Export dp_tech_alias_map hoàn tất")
    print(f"{'=' * 55}")
    print(f"  Số alias     : {len(aliases)}")
    print(f"  Output       : {output_path}")
    print(f"{'=' * 55}\n")


if __name__ == "__main__":
    app()
