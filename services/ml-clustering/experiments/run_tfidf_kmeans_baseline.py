"""
Baseline đơn giản để so sánh với model production (graph+content fusion +
HDBSCAN, xem src/features/feature_pipeline.py + src/clustering/trainer.py):
TF-IDF trên TEXT THẬT (tên tech + article MENTIONS + job REQUIRES) rồi
KMeans thuần — KHÔNG graph embedding (không node2vec, không graph_stats,
không skill jaccard, không bag-of-company-id/bag-of-job-id "tfidf" hiện có ở
graph_features.py — 2 block đó mã hoá QUAN HỆ đồ thị (company/job nào dùng
tech), không phải nội dung văn bản).

Mục đích: định lượng graph+content fusion tốt hơn 1 baseline content-only bao
nhiêu, thay vì chỉ giả định qua comment trong params.yaml.

KHÔNG phải 1 DVC stage (không có trong dvc.yaml) — công cụ nghiên cứu one-off
chạy thủ công, cùng tinh thần experiments/run_feature_ablation.py.

So sánh công bằng — DÙNG CHUNG với Stage 02/03:
  - Cùng snapshot tag, cùng canonicalize_technology_snapshot + noise_filter
    → cùng tech universe (N) với model production.
  - Cùng edges_tech_related_tech (ground truth RELATED_TO) + cùng hàm
    evaluator.evaluate_clustering / compute_related_split_ratio.
  - Cùng tfidf_min_df/tfidf_max_features và cùng kmeans.n_clusters_grid từ
    params.yaml (không tự bịa hyperparameter mới cho baseline).

Nguồn text — snapshot cũ (data/raw/snapshot_<tag>/) chỉ lưu article.title và
job.title (đủ cho content_features/job_tfidf hiện tại, vốn không cần full
text). Baseline này cần content thật hơn nên mặc định (--live) fetch trực
tiếp article.content + job.description/requirement từ Neo4j (xem
src/data/neo4j_loader.fetch_articles/fetch_jobs — đã bổ sung 2 cột này).
Dùng --offline để bỏ qua Neo4j, chỉ dùng title có sẵn trong snapshot — tín
hiệu yếu hơn nhiều (title trung bình vài từ), chỉ nên dùng khi không có kết
nối Neo4j.

CLI:
    cd services/ml-clustering
    python -m experiments.run_tfidf_kmeans_baseline --params params.yaml
    python -m experiments.run_tfidf_kmeans_baseline --offline   # không cần Neo4j

Output:
    experiments/results/tfidf_kmeans_baseline_<tag>.csv
    Bảng so sánh baseline vs champion (data/metrics/<tag>/best_metrics.json) in
    ra stdout.
"""

from __future__ import annotations

import json
from pathlib import Path
from typing import Any

import numpy as np
import pandas as pd
import typer
from loguru import logger
from sklearn.feature_extraction.text import TfidfVectorizer

app = typer.Typer(add_completion=False, help="TF-IDF + KMeans baseline (không graph) so với champion production")

MODULE_ROOT = Path(__file__).resolve().parents[1]

REPORT_COLUMNS = [
    "model",
    "n_total",
    "n_clusters",
    "noise_ratio",
    "silhouette",
    "davies_bouldin",
    "calinski_harabasz",
    "dbcv",
    "related_pairs_evaluated",
    "related_pairs_split_ratio",
]


def build_tech_text_corpus(
    tech_ids: list[str],
    df_technologies: pd.DataFrame,
    df_edges_mentions: pd.DataFrame,
    df_articles: pd.DataFrame,
    df_edges_job_requires_tech: pd.DataFrame,
    df_jobs: pd.DataFrame,
) -> list[str]:
    """
    Ghép 1 document text/tech, ĐÚNG THỨ TỰ `tech_ids`:
      tên tech (+ category) + title/content mọi Article MENTIONS tech
      + title/description/requirement mọi Job REQUIRES tech.

    Không dùng bất kỳ thông tin cấu trúc đồ thị nào khác (không company_id,
    không job_id làm token, không degree/pagerank) — chỉ text tự nhiên, đúng
    tinh thần "content-only, no graph".

    Tech không có Article/Job nào (hiếm, vd tech mới canonicalize) → document
    chỉ còn tên — vẫn khác rỗng nên TfidfVectorizer không raise, dù tín hiệu
    yếu (tương đương "zero-vector" content_features.py gặp phải, ở đây baseline
    không có embedding fallback theo tên nên chỉ còn đúng chuỗi tên).
    """
    name_map = df_technologies.set_index("tech_id")["name"].to_dict()
    category_map = (
        df_technologies.set_index("tech_id")["category"].to_dict() if "category" in df_technologies.columns else {}
    )

    mentions_by_tech = (
        df_edges_mentions.groupby("tech_id")["article_id"].apply(list).to_dict() if not df_edges_mentions.empty else {}
    )
    requires_by_tech = (
        df_edges_job_requires_tech.groupby("tech_id")["job_id"].apply(list).to_dict()
        if not df_edges_job_requires_tech.empty
        else {}
    )

    def _row_text(row: pd.Series, cols: list[str]) -> str:
        return " ".join(str(row.get(c, "") or "") for c in cols)

    article_text = (
        df_articles.set_index("article_id").apply(lambda r: _row_text(r, ["title", "content"]), axis=1).to_dict()
        if not df_articles.empty
        else {}
    )
    job_text = (
        df_jobs.set_index("job_id")
        .apply(lambda r: _row_text(r, ["title", "description", "requirement"]), axis=1)
        .to_dict()
        if not df_jobs.empty
        else {}
    )

    docs: list[str] = []
    for tech_id in tech_ids:
        parts = [str(name_map.get(tech_id, "") or ""), str(category_map.get(tech_id, "") or "")]
        parts += [article_text.get(aid, "") for aid in mentions_by_tech.get(tech_id, [])]
        parts += [job_text.get(jid, "") for jid in requires_by_tech.get(tech_id, [])]
        doc = " ".join(p for p in parts if p).strip()
        docs.append(doc or str(name_map.get(tech_id, tech_id)))

    n_name_only = sum(1 for t, d in zip(tech_ids, docs) if d.strip() == str(name_map.get(t, t)).strip())
    logger.info(
        "build_tech_text_corpus: {} techs, {} chỉ có tên (không Article/Job nào) — {:.1f}%",
        len(docs),
        n_name_only,
        100 * n_name_only / len(docs) if docs else 0.0,
    )
    return docs


def _fit_tfidf(corpus: list[str], min_df: int, max_features: int) -> tuple[TfidfVectorizer, Any]:
    """
    Fit TF-IDF, tự hạ min_df=1 nếu min_df cấu hình (tuned cho quy mô production)
    làm rỗng vocab — dataset dev/test nhỏ hơn giả định trong params.yaml.
    """
    safe_min_df = min(min_df, len(corpus)) if corpus else min_df
    vectorizer = TfidfVectorizer(min_df=safe_min_df, max_features=max_features, norm="l2")
    try:
        X = vectorizer.fit_transform(corpus)
        if X.shape[1] == 0:
            raise ValueError("0 features sau khi prune")
    except ValueError as exc:
        logger.warning("TF-IDF min_df={} thất bại ({}) — thử lại với min_df=1.", safe_min_df, exc)
        vectorizer = TfidfVectorizer(min_df=1, max_features=max_features, norm="l2")
        X = vectorizer.fit_transform(corpus)
    return vectorizer, X


def run_kmeans_grid(
    X: Any,
    tech_ids: list[str],
    n_clusters_grid: list[int],
    n_init: int,
    random_state: int,
    df_related: pd.DataFrame | None,
) -> list[dict[str, Any]]:
    """
    Chạy KMeans cho từng n_clusters trong grid, trả về list metrics (1 dict/trial).
    Loại giá trị n_clusters >= n_samples (sklearn không cho phép) — log rõ thay
    vì âm thầm bỏ qua, vì grid mặc định tuned cho quy mô production (~250 tech).
    """
    from src.clustering.evaluator import compute_related_split_ratio, evaluate_clustering
    from src.clustering.trainer import train_kmeans

    n_samples = X.shape[0]
    X_dense = X.toarray() if hasattr(X, "toarray") else np.asarray(X)

    trials = []
    for k in n_clusters_grid:
        if k >= n_samples:
            logger.warning("Bỏ qua n_clusters={} — >= n_samples={} (dataset nhỏ hơn grid mặc định).", k, n_samples)
            continue
        model, labels = train_kmeans(X_dense, n_clusters=k, n_init=n_init, random_state=random_state)
        metrics = evaluate_clustering(X_dense, labels, model)
        if df_related is not None:
            metrics.update(compute_related_split_ratio(df_related, tech_ids, labels))
        metrics["n_clusters_requested"] = k
        trials.append(metrics)
        logger.info(
            "KMeans k={}: silhouette={} davies_bouldin={} related_split_ratio={}",
            k,
            metrics["silhouette"],
            metrics["davies_bouldin"],
            metrics.get("related_pairs_split_ratio"),
        )

    if not trials:
        raise RuntimeError(
            f"Không trial KMeans nào chạy được — n_samples={n_samples} quá nhỏ so với "
            f"n_clusters_grid={n_clusters_grid}. Giảm grid trong params.yaml hoặc dùng dataset lớn hơn."
        )
    return trials


def select_best_by_silhouette(trials: list[dict[str, Any]]) -> dict[str, Any]:
    """
    Chọn trial tốt nhất theo Silhouette — DBCV (metric production dùng để chọn
    champion HDBSCAN) không áp dụng được cho KMeans (không phải density-based,
    không có relative_validity_). Silhouette là metric chuẩn cho cụm dạng lồi
    (giả định của KMeans) và cũng được evaluator tính cho MỌI model → là căn cứ
    công bằng nhất để vừa chọn best-k cho baseline, vừa so sánh chéo với champion.
    """
    scored = [t for t in trials if not (isinstance(t["silhouette"], float) and np.isnan(t["silhouette"]))]
    if not scored:
        raise RuntimeError("Không trial nào có Silhouette hợp lệ (mọi trial <2 cụm?).")
    return max(scored, key=lambda t: t["silhouette"])


def _load_champion_metrics(tag: str) -> dict[str, Any] | None:
    from conf.config import metrics_dir

    path = metrics_dir(tag) / "best_metrics.json"
    if not path.exists():
        logger.warning("Không tìm thấy champion metrics tại {} — chỉ in kết quả baseline, không so sánh.", path)
        return None
    return json.loads(path.read_text(encoding="utf-8"))


@app.command()
def main(
    params: str = typer.Option("params.yaml", help="Đường dẫn params.yaml (snapshot tag + hyperparameter dùng chung)"),
    offline: bool = typer.Option(
        False,
        "--offline/--live",
        help="--offline: chỉ dùng title có sẵn trong snapshot (không cần Neo4j). "
        "--live (mặc định): fetch article.content + job.description/requirement từ Neo4j — tín hiệu thật hơn.",
    ),
    output: str = typer.Option(
        "", help="Đường dẫn CSV kết quả (mặc định: experiments/results/tfidf_kmeans_baseline_<tag>.csv)"
    ),
) -> None:
    from conf.config import load_params, snapshot_dir

    from src.data.neo4j_loader import load_parquet
    from src.features.noise_filter import filter_noise
    from src.features.tech_aliases import canonicalize_technology_snapshot

    params_obj = load_params(params)
    tag = params_obj.snapshot.tag
    fp = params_obj.features
    snap_dir = snapshot_dir(tag)
    logger.info("TF-IDF + KMeans baseline | tag={} mode={}", tag, "offline" if offline else "live")

    # --- 1. Tech universe + edges: TÁI DÙNG snapshot + canonicalize + noise_filter
    # y hệt Stage 02, để N và ground-truth RELATED_TO giống hệt model champion.
    df_tech = load_parquet(snap_dir / "technologies.parquet")
    df_edges_mentions = load_parquet(snap_dir / "edges_article_mentions_tech.parquet")
    df_edges_company_uses_tech = load_parquet(snap_dir / "edges_company_uses_tech.parquet")
    df_edges_job_requires_tech = load_parquet(snap_dir / "edges_job_requires_tech.parquet")
    df_edges_tech_related = load_parquet(snap_dir / "edges_tech_related_tech.parquet")

    canonicalized = canonicalize_technology_snapshot(
        df_tech=df_tech,
        df_edges_mentions=df_edges_mentions,
        df_edges_company_uses_tech=df_edges_company_uses_tech,
        df_edges_job_requires_tech=df_edges_job_requires_tech,
        df_edges_tech_related=df_edges_tech_related,
    )
    df_tech = canonicalized.technologies
    df_edges_mentions = canonicalized.edges_article_mentions_tech
    df_edges_job_requires_tech = canonicalized.edges_job_requires_tech
    df_edges_tech_related = canonicalized.edges_tech_related_tech

    if fp.noise_filter.enabled:
        df_tech = filter_noise(df_tech, df_edges_job_requires_tech, fp.noise_filter)

    tech_ids = df_tech["tech_id"].tolist()
    logger.info("Tech universe (sau canonicalize+noise_filter): {} techs — giống Stage 02/03.", len(tech_ids))

    # --- 2. Text nguồn (content thật hoặc title-only)
    if offline:
        logger.warning(
            "--offline: dùng title-only — tín hiệu yếu hơn --live (snapshot cũ không lưu content/description)."
        )
        df_articles = load_parquet(snap_dir / "articles.parquet")
        df_jobs = load_parquet(snap_dir / "jobs.parquet")
        for col in ("content",):
            if col not in df_articles.columns:
                df_articles[col] = ""
        for col in ("description", "requirement"):
            if col not in df_jobs.columns:
                df_jobs[col] = ""
    else:
        logger.info("Fetching article.content + job.description/requirement từ Neo4j...")
        from src.data.neo4j_loader import close_driver, fetch_articles, fetch_jobs

        df_articles = fetch_articles(only_with_embedding=False)
        df_jobs = fetch_jobs()
        close_driver()

    # --- 3. Corpus + TF-IDF (KHÔNG graph — chỉ text)
    corpus = build_tech_text_corpus(
        tech_ids=tech_ids,
        df_technologies=df_tech,
        df_edges_mentions=df_edges_mentions,
        df_articles=df_articles,
        df_edges_job_requires_tech=df_edges_job_requires_tech,
        df_jobs=df_jobs,
    )
    logger.info("Building TF-IDF (min_df={}, max_features={})...", fp.tfidf_min_df, fp.tfidf_max_features)
    vectorizer, X = _fit_tfidf(corpus, min_df=fp.tfidf_min_df, max_features=fp.tfidf_max_features)
    logger.info("TF-IDF matrix: shape={} vocab={}", X.shape, len(vectorizer.get_feature_names_out()))

    # --- 4. KMeans grid (cùng grid với params.yaml clustering.kmeans) + chọn best theo Silhouette
    df_related = df_edges_tech_related if not df_edges_tech_related.empty else None
    trials = run_kmeans_grid(
        X,
        tech_ids,
        n_clusters_grid=params_obj.clustering.kmeans.n_clusters_grid,
        n_init=params_obj.clustering.kmeans.n_init,
        random_state=params_obj.clustering.kmeans.random_state,
        df_related=df_related,
    )
    best = select_best_by_silhouette(trials)
    best["model"] = "baseline_tfidf_kmeans"

    # --- 5. So sánh với champion production (best_metrics.json cùng tag)
    champion = _load_champion_metrics(tag)
    rows: list[dict[str, Any]] = [best]
    if champion is not None:
        champion_row = {**champion, "model": "production_champion (graph+content fusion)"}
        rows.append(champion_row)

    # --- 6. Ghi CSV
    out_path = Path(output) if output else MODULE_ROOT / "experiments" / "results" / f"tfidf_kmeans_baseline_{tag}.csv"
    out_path.parent.mkdir(parents=True, exist_ok=True)
    all_fields = sorted({k for r in rows for k in r.keys()} | set(REPORT_COLUMNS))
    df_out = pd.DataFrame(rows)
    df_out.to_csv(out_path, index=False, columns=[c for c in all_fields if c in df_out.columns])
    logger.info("Kết quả CSV → {}", out_path)

    # --- 7. Bảng tóm tắt ra stdout
    print(f"\n{'=' * 100}\n  TF-IDF + KMeans Baseline (no graph) vs Production Champion | tag={tag}\n{'=' * 100}")
    header = " | ".join(f"{c:<26}" for c in REPORT_COLUMNS)
    print(header)
    print("-" * len(header))
    for r in rows:
        print(" | ".join(f"{str(r.get(c, ''))[:26]:<26}" for c in REPORT_COLUMNS))
    print(f"{'=' * 100}")
    print(f"  n_clusters_requested (baseline, best k) : {best.get('n_clusters_requested')}")
    if champion is not None:
        sil_delta = best["silhouette"] - champion.get("silhouette", float("nan"))
        print(
            f"  Silhouette: baseline={best['silhouette']:.4f}  champion={champion.get('silhouette', float('nan')):.4f}"
            f"  (Δ champion-baseline = {-sil_delta:+.4f})"
        )
        b_ratio = best.get("related_pairs_split_ratio")
        c_ratio = champion.get("related_pairs_split_ratio")
        print(f"  RELATED_TO split ratio (thấp hơn = tốt hơn): baseline={b_ratio}  champion={c_ratio}")
    print(f"{'=' * 100}\n")

    return 0


if __name__ == "__main__":
    app()
