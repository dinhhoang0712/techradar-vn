"""
Stage 02 — FEATURES: snapshot parquet → ma trận đặc trưng X cho clustering.

CLI:
    python -m pipelines.stage_02_features --params params.yaml

Output: data/features/<tag>/{X.npy, tech_ids.parquet, feature_meta.json}
"""

import typer
from loguru import logger

app = typer.Typer(add_completion=False, help="Build feature matrix")


@app.command()
def main(
    params: str = typer.Option("params.yaml", help="Đường dẫn params.yaml"),
) -> None:
    """
    Yêu cầu logic (theo thứ tự, có log tiến độ):

      1. `params_obj = load_params(params)`. Lấy `tag = params_obj.snapshot.tag`.
      2. Đọc snapshot parquet.
      3. Mở Neo4j driver (chỉ để gọi GDS — KHÔNG đọc lại data).
      4. Project GDS graph.
      5. Tính từng GDS feature theo flag trong params_obj.features.
      6. Drop GDS graph + đóng driver.
      7. Tính content feature (article embedding aggregate + fallback).
      8. Tính graph_stats.
      9. Build TF-IDF nếu enabled.
     10. Build feature matrix.
     11. Lưu features.
     12. In summary.
     13. Return 0 nếu OK.
    """
    import numpy as np
    import pandas as pd
    from sklearn.preprocessing import normalize as sk_normalize

    from conf.config import features_dir, load_params, snapshot_dir
    from src.data.neo4j_loader import load_parquet
    from src.validation import SnapshotValidationError, validate_stage2_snapshot
    from src.features.content_features import (
        aggregate_article_embeddings,
        embed_tech_names_fallback,
        fill_missing_content_with_name_embedding,
    )
    from src.features.feature_pipeline import build_feature_matrix, save_features
    from src.features.graph_features import (
        build_company_tech_tfidf,
        build_job_tech_tfidf,
        compute_neighborhood_stats,
    )
    from src.features.tech_aliases import canonicalize_technology_snapshot

    # 1. Params
    params_obj = load_params(params)
    tag = params_obj.snapshot.tag
    fp = params_obj.features
    snap_dir = snapshot_dir(tag)
    logger.info("Stage 02 — FEATURES | tag={}", tag)

    # 2. Load snapshot DataFrames
    logger.info("Đọc snapshot từ {} ...", snap_dir)
    df_tech      = load_parquet(snap_dir / "technologies.parquet")
    df_company   = load_parquet(snap_dir / "companies.parquet")
    df_article   = load_parquet(snap_dir / "articles.parquet")
    df_job       = load_parquet(snap_dir / "jobs.parquet")
    df_edges_mentions          = load_parquet(snap_dir / "edges_article_mentions_tech.parquet")
    df_edges_company_uses_tech = load_parquet(snap_dir / "edges_company_uses_tech.parquet")
    df_edges_job_requires_tech = load_parquet(snap_dir / "edges_job_requires_tech.parquet")
    df_edges_job_requires_skill = load_parquet(snap_dir / "edges_job_requires_skill.parquet")
    df_edges_tech_related      = load_parquet(snap_dir / "edges_tech_related_tech.parquet")
    logger.info("Snapshot loaded: {} techs, {} articles, {} jobs", len(df_tech), len(df_article), len(df_job))

    try:
        validation_report = validate_stage2_snapshot(
            df_tech=df_tech,
            df_company=df_company,
            df_article=df_article,
            df_job=df_job,
            df_edges_mentions=df_edges_mentions,
            df_edges_company_uses_tech=df_edges_company_uses_tech,
            df_edges_job_requires_tech=df_edges_job_requires_tech,
            df_edges_job_requires_skill=df_edges_job_requires_skill,
            df_edges_tech_related=df_edges_tech_related,
            feature_params=fp,
        )
    except SnapshotValidationError as exc:
        logger.error("Data validation failed before feature build:\n{}", exc)
        raise typer.Exit(code=1)

    logger.info(
        "Data validation OK: {} checks, {} warning(s)",
        validation_report.checks_run,
        len(validation_report.warnings),
    )
    for issue in validation_report.warnings[:10]:
        logger.warning("Data validation warning [{}]: {}", issue.check, issue.message)
    if len(validation_report.warnings) > 10:
        logger.warning(
            "Data validation warning: {} more warning(s) omitted",
            len(validation_report.warnings) - 10,
        )

    canonicalized = canonicalize_technology_snapshot(
        df_tech=df_tech,
        df_edges_mentions=df_edges_mentions,
        df_edges_company_uses_tech=df_edges_company_uses_tech,
        df_edges_job_requires_tech=df_edges_job_requires_tech,
        df_edges_tech_related=df_edges_tech_related,
    )
    df_tech = canonicalized.technologies
    df_edges_mentions = canonicalized.edges_article_mentions_tech
    df_edges_company_uses_tech = canonicalized.edges_company_uses_tech
    df_edges_job_requires_tech = canonicalized.edges_job_requires_tech
    df_edges_tech_related = canonicalized.edges_tech_related_tech
    if canonicalized.n_merged:
        logger.info(
            "Canonicalized Technology aliases: merged {} duplicate/alias rows; {} techs remain",
            canonicalized.n_merged,
            len(df_tech),
        )

    # 2b. Noise filter — loại tech nodes không hợp lệ trước khi build features
    if fp.noise_filter.enabled:
        from src.features.noise_filter import filter_noise
        df_tech = filter_noise(df_tech, df_edges_job_requires_tech, fp.noise_filter)
        logger.info("Sau noise filter: {} techs còn lại", len(df_tech))

    # 3-6. GDS features — bỏ qua vì AuraDB yêu cầu session-based GDS API riêng
    # Chỉ dùng content embedding + graph stats + TF-IDF
    gds_features: dict = {}
    any_gds = fp.fastrp.enabled or fp.node2vec.enabled or fp.pagerank.enabled or fp.louvain.enabled
    if any_gds:
        logger.warning("GDS flags enabled nhưng AuraDB không hỗ trợ gds.graph.project trực tiếp — bỏ qua GDS.")
    logger.info("GDS features: bỏ qua (tất cả disabled hoặc không khả dụng)")

    # 7. Content feature
    if fp.use_name_embedding:
        # Luôn dùng name embedding — bỏ qua article hoàn toàn (kể cả khi có Article trong DB)
        logger.info("use_name_embedding=true → encode tên tech bằng name embedding (bỏ qua article)...")
        from src.features.content_features import EMB_COLS, EMB_DIM
        names = df_tech["name"].tolist()
        tech_ids = df_tech["tech_id"].tolist()
        embs = embed_tech_names_fallback(names)                      # (N, 768)
        embs = sk_normalize(embs, norm="l2")
        # Giảm chiều name_emb bằng PCA nếu được cấu hình
        if fp.name_emb_pca_components > 0 and fp.name_emb_pca_components < EMB_DIM:
            from sklearn.decomposition import PCA
            pca = PCA(n_components=fp.name_emb_pca_components, random_state=42)
            embs = pca.fit_transform(embs).astype(np.float32)
            emb_cols_used = [f"article_emb_{i}" for i in range(fp.name_emb_pca_components)]
            logger.info("PCA name_emb: {} → {} dims (explained var {:.1f}%)",
                        EMB_DIM, fp.name_emb_pca_components,
                        100 * pca.explained_variance_ratio_.sum())
        else:
            emb_cols_used = EMB_COLS

        content_emb = pd.DataFrame(embs, columns=emb_cols_used)
        content_emb.insert(0, "tech_id", tech_ids)
        content_emb["content_n_articles"] = 0
        logger.info("Name embedding xong: {} techs, {} dims", len(content_emb), embs.shape[1])
    else:
        logger.info("Computing content embeddings (article aggregate)...")
        content_emb = aggregate_article_embeddings(
            df_articles=df_article,
            df_edges_mentions=df_edges_mentions,
            df_technologies=df_tech,
            method=fp.article_embedding_aggregation.method,
            min_articles_per_tech=fp.article_embedding_aggregation.min_articles_per_tech,
        )
        if fp.article_embedding_aggregation.enabled:
            logger.info("Filling missing embeddings with name-based fallback...")
            content_emb = fill_missing_content_with_name_embedding(content_emb, df_tech)

    # 8. Graph stats
    logger.info("Computing neighborhood stats...")
    # df_edges_tech_related has tech_id_a/tech_id_b; rename tech_id_a → tech_id for count_col helper
    df_edges_tech_related_renamed = df_edges_tech_related.rename(columns={"tech_id_a": "tech_id"})
    graph_stats = compute_neighborhood_stats(
        df_technologies=df_tech,
        df_edges_article_mentions_tech=df_edges_mentions,
        df_edges_company_uses_tech=df_edges_company_uses_tech,
        df_edges_job_requires_tech=df_edges_job_requires_tech,
        df_edges_tech_related_tech=df_edges_tech_related_renamed,
    )

    # 9. TF-IDF
    company_tfidf = None
    job_tfidf = None

    if fp.use_company_tfidf:
        logger.info("Building company TF-IDF (max_features={})...", fp.tfidf_max_features)
        company_tfidf = build_company_tech_tfidf(
            df_edges_uses=df_edges_company_uses_tech,
            df_technologies=df_tech,
            min_df=fp.tfidf_min_df,
            max_features=fp.tfidf_max_features,
        )

    if fp.use_job_tfidf:
        logger.info("Building job TF-IDF (max_features={})...", fp.tfidf_max_features)
        job_tfidf = build_job_tech_tfidf(
            df_edges_requires_tech=df_edges_job_requires_tech,
            df_jobs=df_job,
            df_technologies=df_tech,
            min_df=fp.tfidf_min_df,
            max_features=fp.tfidf_max_features,
        )

    # 10. Build feature matrix
    logger.info("Building final feature matrix...")
    X, meta = build_feature_matrix(
        df_technologies=df_tech,
        gds_features=gds_features,
        content_emb=content_emb,
        graph_stats=graph_stats,
        company_tfidf=company_tfidf,
        job_tfidf=job_tfidf,
        params=fp,
    )

    # 11. Save
    out_dir = features_dir(tag)
    logger.info("Saving features → {}", out_dir)
    save_features(X, meta, out_dir)

    # 12. Summary
    n_zero_content = (content_emb["content_n_articles"] == 0).sum()
    pct_zero = 100 * n_zero_content / len(content_emb)
    print(f"\n{'='*50}")
    print(f"  Stage 02 FEATURES hoàn tất | tag={tag}")
    print(f"{'='*50}")
    print(f"  Feature matrix shape : {X.shape}")
    print(f"  n_techs              : {meta.n_techs}")
    print(f"  Original dim         : {meta.original_dim}")
    print(f"  Final dim (after red): {meta.final_dim}")
    print(f"  Scaler               : {meta.scaler_name}")
    print(f"  Reduce dim           : {meta.reduce_dim}")
    print(f"  Zero-content techs   : {n_zero_content} ({pct_zero:.1f}%) — đã dùng fallback")
    print(f"\n  Feature groups:")
    for name, (s, e) in meta.feature_groups.items():
        print(f"    {name:<25} cols {s}..{e-1}  ({e-s} dim)")
    print(f"{'='*50}\n")

    return 0


if __name__ == "__main__":
    app()
