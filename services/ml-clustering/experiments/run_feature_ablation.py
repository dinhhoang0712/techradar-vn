"""
Ablation study cho feature engineering — chạy "baseline_all" (params.yaml gốc,
mọi feature bật như hiện tại) rồi tắt LẦN LƯỢT từng nhóm feature
(leave-one-out: node2vec, name embedding, job TF-IDF, skill jaccard, article
temporal), chạy lại Stage 02 + Stage 03 cho mỗi biến thể, rồi so sánh
Silhouette / DBCV / noise_ratio / RELATED_TO-split-ratio để đo đóng góp thực
sự của từng nhóm feature — thay vì chỉ đoán qua comment trong params.yaml.

KHÔNG phải 1 DVC stage (không có trong dvc.yaml) — đây là công cụ nghiên cứu
one-off chạy thủ công, không chạy tự động trong pipeline production.

Yêu cầu:
  - Đã có snapshot thô sẵn tại `data/raw/snapshot_<tag>/*.parquet` từ 1 lần
    chạy `stage_01_extract` trước đó — script này KHÔNG tự pull dữ liệu từ
    Neo4j, chỉ copy snapshot đã có sang tag riêng cho từng biến thể.
  - Mỗi biến thể chạy Stage 02 + Stage 03 như subprocess CLI bình thường,
    KHÔNG cần Neo4j (fastrp/pagerank/louvain/wcc đã tắt mặc định — xem
    src/features/gds_features.py) và KHÔNG cần LLM API key (Stage 04 label
    không được gọi ở đây).

CLI:
    cd services/ml-clustering
    python -m experiments.run_feature_ablation --params params.yaml

Output:
    experiments/results/feature_ablation_<base_tag>.csv
    Bảng so sánh in ra stdout.
"""

from __future__ import annotations

import copy
import csv
import json
import shutil
import subprocess
import sys
from pathlib import Path
from typing import Any

import typer
import yaml
from loguru import logger

app = typer.Typer(add_completion=False, help="Feature ablation study (leave-one-out)")

MODULE_ROOT = Path(__file__).resolve().parents[1]

# Leave-one-out: mỗi variant tắt ĐÚNG 1 nhóm feature so với baseline_all, để
# đóng góp của nhóm đó có thể đọc trực tiếp bằng cách so với hàng baseline.
DEFAULT_VARIANTS: list[dict[str, Any]] = [
    {"name": "baseline_all", "overrides": {}},
    {"name": "no_node2vec", "overrides": {"features.node2vec.enabled": False}},
    {"name": "no_name_embedding", "overrides": {"features.use_name_embedding": False}},
    {"name": "no_job_tfidf", "overrides": {"features.use_job_tfidf": False}},
    {"name": "no_skill_jaccard", "overrides": {"features.use_skill_jaccard": False}},
    {"name": "no_article_temporal", "overrides": {"features.use_article_temporal_stats": False}},
]

REPORT_COLUMNS = [
    "variant",
    "n_clusters",
    "noise_ratio",
    "silhouette",
    "davies_bouldin",
    "dbcv",
    "related_pairs_split_ratio",
    "error",
]


def _set_nested(data: dict, dotted_key: str, value: Any) -> None:
    """
    Set data['a']['b']['c'] = value từ dotted_key='a.b.c'.

    Chỉ đòi hỏi các segment TRUNG GIAN (vd 'a', 'b') đã tồn tại trong dict —
    báo lỗi sớm nếu variant trỏ nhầm path. Segment CUỐI (leaf) được phép thêm
    mới: một số field (vd use_skill_jaccard/use_article_temporal_stats) chỉ
    có default trong Pydantic (conf/config.py), chưa từng ghi tường minh
    trong params.yaml — Pydantic vẫn đọc đúng khi key này xuất hiện lần đầu
    lúc `Params.model_validate()`.
    """
    keys = dotted_key.split(".")
    node = data
    for k in keys[:-1]:
        if k not in node:
            raise KeyError(f"Đường dẫn '{dotted_key}' không tồn tại trong params (thiếu '{k}')")
        node = node[k]
    node[keys[-1]] = value


def _copy_snapshot(base_tag: str, variant_tag: str) -> None:
    """
    Copy data/raw/snapshot_<base_tag> → snapshot_<variant_tag> nếu chưa có,
    để Stage 02 của biến thể đọc được input mà không đụng vào snapshot gốc.
    Copy file (không symlink) để không phụ thuộc hệ điều hành/filesystem.
    """
    from conf.config import snapshot_dir

    src = snapshot_dir(base_tag)
    dst = snapshot_dir(variant_tag)  # tạo sẵn thư mục rỗng nếu chưa có
    if any(dst.iterdir()):
        logger.info("Snapshot {} đã có sẵn — bỏ qua copy.", variant_tag)
        return

    files = list(src.glob("*.parquet"))
    if not files:
        raise FileNotFoundError(
            f"Không tìm thấy snapshot thô tại {src} — cần chạy "
            f"`python -m pipelines.stage_01_extract --params <base_params>` trước."
        )
    for f in files:
        shutil.copy2(f, dst / f.name)
    logger.info("Copied snapshot {} → {} ({} files)", base_tag, variant_tag, len(files))


def _run_stage(module: str, params_path: Path) -> None:
    cmd = [sys.executable, "-m", module, "--params", str(params_path)]
    logger.info("Chạy: {}", " ".join(cmd))
    result = subprocess.run(cmd, cwd=MODULE_ROOT)
    if result.returncode != 0:
        raise RuntimeError(f"{module} thất bại (exit={result.returncode}) — xem log ở trên.")


@app.command()
def main(
    params: str = typer.Option("params.yaml", help="params.yaml gốc (baseline), dùng làm nền cho mọi biến thể"),
    output: str = typer.Option(
        "", help="Đường dẫn CSV kết quả (mặc định: experiments/results/feature_ablation_<tag>.csv)"
    ),
) -> None:
    """
    Với mỗi variant trong DEFAULT_VARIANTS:
      1. Deep-copy params gốc, đổi snapshot.tag → "<base_tag>__ablation_<name>",
         áp override feature flag của variant.
      2. Copy snapshot thô sang tag mới (idempotent).
      3. Ghi params biến thể ra file tạm, chạy Stage 02 rồi Stage 03 (subprocess).
      4. Đọc best_metrics.json → gộp vào bảng kết quả.
    Một variant lỗi (vd constraint chọn model quá chặt) không dừng cả batch —
    ghi lại error, tiếp tục variant kế tiếp.
    """
    from conf.config import metrics_dir

    base_params_path = Path(params).resolve()
    with open(base_params_path, encoding="utf-8") as f:
        base_raw = yaml.safe_load(f)

    base_tag = base_raw["snapshot"]["tag"]
    tmp_dir = MODULE_ROOT / "experiments" / "_tmp_params"
    tmp_dir.mkdir(parents=True, exist_ok=True)

    logger.info("Feature ablation study: base_tag={} | {} variant(s)", base_tag, len(DEFAULT_VARIANTS))

    rows: list[dict[str, Any]] = []
    for variant in DEFAULT_VARIANTS:
        name = variant["name"]
        variant_tag = f"{base_tag}__ablation_{name}"
        logger.info("\n{}\n Variant: {} (tag={})\n{}", "=" * 60, name, variant_tag, "=" * 60)

        variant_raw = copy.deepcopy(base_raw)
        variant_raw["snapshot"]["tag"] = variant_tag
        for dotted_key, value in variant["overrides"].items():
            _set_nested(variant_raw, dotted_key, value)

        variant_params_path = tmp_dir / f"{name}.yaml"
        with open(variant_params_path, "w", encoding="utf-8") as f:
            yaml.safe_dump(variant_raw, f, allow_unicode=True)

        row: dict[str, Any] = {"variant": name}
        try:
            _copy_snapshot(base_tag, variant_tag)
            _run_stage("pipelines.stage_02_features", variant_params_path)
            _run_stage("pipelines.stage_03_train", variant_params_path)

            metrics_path = metrics_dir(variant_tag) / "best_metrics.json"
            if metrics_path.exists():
                row.update(json.loads(metrics_path.read_text(encoding="utf-8")))
            else:
                row["error"] = f"Thiếu {metrics_path} sau khi chạy — kiểm tra log Stage 03."
        except Exception as exc:  # noqa: BLE001 — muốn bắt mọi lỗi để tiếp tục variant sau
            logger.error("Variant {} thất bại: {}", name, exc)
            row["error"] = str(exc)

        rows.append(row)

    # --- Ghi CSV ---
    out_path = Path(output) if output else MODULE_ROOT / "experiments" / "results" / f"feature_ablation_{base_tag}.csv"
    out_path.parent.mkdir(parents=True, exist_ok=True)
    all_fields = sorted({k for r in rows for k in r.keys()} | set(REPORT_COLUMNS))
    with open(out_path, "w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=all_fields)
        writer.writeheader()
        writer.writerows(rows)
    logger.info("Kết quả CSV → {}", out_path)

    # --- Bảng tóm tắt ra stdout ---
    print(f"\n{'=' * 100}\n  Feature Ablation Study | base_tag={base_tag}\n{'=' * 100}")
    header = " | ".join(f"{c:<22}" for c in REPORT_COLUMNS)
    print(header)
    print("-" * len(header))
    for r in rows:
        print(" | ".join(f"{str(r.get(c, ''))[:22]:<22}" for c in REPORT_COLUMNS))
    print(f"{'=' * 100}\n")


if __name__ == "__main__":
    app()
