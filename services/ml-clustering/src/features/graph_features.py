"""
Hand-crafted feature từ graph (không cần GDS plugin) — bổ sung cho FastRP/PageRank.

Mục tiêu: khai thác tín hiệu structural mà GDS embedding có thể bỏ sót, cụ thể:
  - Bag-of-Companies: tech được dùng bởi cùng nhóm công ty → gần nhau.
  - Bag-of-Jobs / Bag-of-JobTitles: tech yêu cầu trong cùng vai trò → gần nhau.
  - Một số stat đơn giản: số article mention, số job require, ratio…

Toàn bộ tính trên các DataFrame snapshot, không cần Neo4j.
"""

from __future__ import annotations

import logging

import numpy as np
import pandas as pd
import scipy.sparse as sp
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.preprocessing import normalize

logger = logging.getLogger(__name__)


# ---------------------------------------------------------------------------
# Co-occurrence sparse matrices
# ---------------------------------------------------------------------------

def build_company_tech_tfidf(
    df_edges_uses: pd.DataFrame,
    df_technologies: pd.DataFrame,
    min_df: int = 2,
    max_features: int = 500,
) -> tuple[sp.csr_matrix, list[str]]:
    """
    Bag-of-Companies cho mỗi Technology (TF-IDF).
    Coi mỗi tech là 1 document, company_id là token.
    Trả về (csr_matrix shape (n_techs, n_features), feature_names).
    """
    tech_ids = df_technologies["tech_id"].tolist()

    # Tạo "document" cho mỗi tech: list company_id dùng tech đó
    tech_to_companies: dict[str, list[str]] = {t: [] for t in tech_ids}
    for _, row in df_edges_uses.iterrows():
        if row["tech_id"] in tech_to_companies:
            tech_to_companies[row["tech_id"]].append(row["company_id"])

    corpus = [tech_to_companies[t] for t in tech_ids]

    vectorizer = TfidfVectorizer(
        analyzer=lambda x: x,      # token đã là list, không cần tokenize
        min_df=min_df,
        max_features=max_features,
        norm="l2",
    )
    X = vectorizer.fit_transform(corpus)
    feature_names = vectorizer.get_feature_names_out().tolist()

    logger.info("build_company_tech_tfidf: shape=%s, features=%d", X.shape, len(feature_names))
    return X, feature_names


def build_job_tech_tfidf(
    df_edges_requires_tech: pd.DataFrame,
    df_jobs: pd.DataFrame,
    df_technologies: pd.DataFrame,
    min_df: int = 2,
    max_features: int = 500,
) -> tuple[sp.csr_matrix, list[str]]:
    """
    Bag-of-Jobs cho mỗi Technology (TF-IDF).
    Token = "{title_normalized}_{level}" để có ngữ nghĩa hơn job_id.
    Trả về (csr_matrix, feature_names).
    """
    tech_ids = df_technologies["tech_id"].tolist()

    # Chuẩn hoá title: lowercase + bỏ ký tự đặc biệt
    df_jobs = df_jobs.copy()
    df_jobs["title_norm"] = (
        df_jobs["title"]
        .str.lower()
        .str.replace(r"[^a-z0-9 ]", "", regex=True)
        .str.strip()
    )
    df_jobs["token"] = df_jobs["title_norm"] + "_" + df_jobs["level"].str.lower().fillna("unknown")

    job_token_map = df_jobs.set_index("job_id")["token"].to_dict()

    tech_to_tokens: dict[str, list[str]] = {t: [] for t in tech_ids}
    for _, row in df_edges_requires_tech.iterrows():
        if row["tech_id"] in tech_to_tokens:
            token = job_token_map.get(row["job_id"], "unknown")
            tech_to_tokens[row["tech_id"]].append(token)

    corpus = [tech_to_tokens[t] for t in tech_ids]

    vectorizer = TfidfVectorizer(
        analyzer=lambda x: x,
        min_df=min_df,
        max_features=max_features,
        norm="l2",
    )
    X = vectorizer.fit_transform(corpus)
    feature_names = vectorizer.get_feature_names_out().tolist()

    logger.info("build_job_tech_tfidf: shape=%s, features=%d", X.shape, len(feature_names))
    return X, feature_names


def build_skill_tech_jaccard(
    df_edges_requires_skill: pd.DataFrame,
    df_edges_requires_tech: pd.DataFrame,
    df_technologies: pd.DataFrame,
) -> pd.DataFrame:
    """
    Bắc cầu Tech ← Job → Skill để tính:
      - n_unique_jobs: số job yêu cầu tech này
      - n_unique_skills_share: số skill mà các job đó cùng yêu cầu
      - mean_jaccard_with_top10: độ tương đồng job-set với 10 tech phổ biến nhất
    """
    tech_ids = df_technologies["tech_id"].tolist()

    # tech → set of jobs
    tech_jobs: dict[str, set] = {t: set() for t in tech_ids}
    for _, row in df_edges_requires_tech.iterrows():
        if row["tech_id"] in tech_jobs:
            tech_jobs[row["tech_id"]].add(row["job_id"])

    # job → set of skills
    job_skills: dict[str, set] = {}
    for _, row in df_edges_requires_skill.iterrows():
        job_skills.setdefault(row["job_id"], set()).add(row["skill_id"])

    # Top 10 tech theo số job
    top10 = sorted(tech_jobs.keys(), key=lambda t: -len(tech_jobs[t]))[:10]
    top10_job_sets = [tech_jobs[t] for t in top10]

    rows = []
    for tech_id in tech_ids:
        jobs = tech_jobs[tech_id]
        n_jobs = len(jobs)

        # Skills từ các job liên quan
        shared_skills: set = set()
        for j in jobs:
            shared_skills |= job_skills.get(j, set())

        # Jaccard với top-10
        jaccards = []
        for top_jobs in top10_job_sets:
            union = jobs | top_jobs
            inter = jobs & top_jobs
            jaccards.append(len(inter) / len(union) if union else 0.0)

        rows.append({
            "tech_id":                tech_id,
            "n_unique_jobs":          n_jobs,
            "n_unique_skills_share":  len(shared_skills),
            "mean_jaccard_with_top10": float(np.mean(jaccards)) if jaccards else 0.0,
        })

    df = pd.DataFrame(rows).set_index("tech_id").loc[tech_ids].reset_index()
    logger.info("build_skill_tech_jaccard: %d rows", len(df))
    return df


# ---------------------------------------------------------------------------
# Scalar features
# ---------------------------------------------------------------------------

def compute_neighborhood_stats(
    df_technologies: pd.DataFrame,
    df_edges_article_mentions_tech: pd.DataFrame,
    df_edges_company_uses_tech: pd.DataFrame,
    df_edges_job_requires_tech: pd.DataFrame,
    df_edges_tech_related_tech: pd.DataFrame,
) -> pd.DataFrame:
    """
    Đếm láng giềng theo từng quan hệ + log-transform để giảm bias top-degree node.
    Output: tech_id | n_articles | n_companies | n_jobs | n_related |
            log_* | ratio_jobs_per_company
    """
    tech_ids = df_technologies["tech_id"].tolist()
    base = pd.DataFrame({"tech_id": tech_ids})

    def count_col(df: pd.DataFrame, col: str, new_name: str) -> pd.Series:
        if df.empty or "tech_id" not in df.columns or col not in df.columns:
            return pd.Series(0, index=tech_ids, name=new_name)
        return (
            df.groupby("tech_id")[col]
            .count()
            .reindex(tech_ids, fill_value=0)
            .rename(new_name)
        )

    n_articles  = count_col(df_edges_article_mentions_tech, "article_id",  "n_articles_mentioning")
    n_companies = count_col(df_edges_company_uses_tech,     "company_id",  "n_companies_using")
    n_jobs      = count_col(df_edges_job_requires_tech,     "job_id",      "n_jobs_requiring")
    n_related   = count_col(df_edges_tech_related_tech,     "tech_id_b",   "n_related_techs")

    df = base.join(n_articles).join(n_companies).join(n_jobs).join(n_related)

    # Log-transform: log1p(x) = log(x+1), tránh log(0)
    df["log_n_articles"]   = np.log1p(df["n_articles_mentioning"])
    df["log_n_companies"]  = np.log1p(df["n_companies_using"])
    df["log_n_jobs"]       = np.log1p(df["n_jobs_requiring"])

    # Tỉ lệ job/company — tech được nhiều job yêu cầu nhưng ít công ty dùng = niche
    df["ratio_jobs_per_company"] = (
        df["n_jobs_requiring"] / df["n_companies_using"].clip(lower=1)
    )

    logger.info("compute_neighborhood_stats: %d rows", len(df))
    return df


# ---------------------------------------------------------------------------
# Article temporal stats
# ---------------------------------------------------------------------------

def compute_article_temporal_stats(
    df_edges_mentions: pd.DataFrame,
    df_articles: pd.DataFrame,
) -> pd.DataFrame:
    """
    Tín hiệu thời gian: first/last mention, skewness ngày, sentiment trung bình.
    Output: tech_id | first_mention_days_ago | last_mention_days_ago |
            mention_recency_skew | mean_sentiment
    """
    now = pd.Timestamp.now(tz="UTC").normalize()

    df_art = df_articles[["article_id", "published_date", "sentiment_score"]].copy()
    df_art["published_date"] = pd.to_datetime(df_art["published_date"], utc=True, errors="coerce")

    merged = df_edges_mentions.merge(df_art, on="article_id", how="left")

    rows = []
    for tech_id, grp in merged.groupby("tech_id"):
        dates = grp["published_date"].dropna()
        sentiments = grp["sentiment_score"].dropna()

        if len(dates) == 0:
            rows.append({
                "tech_id": tech_id,
                "first_mention_days_ago":  -1,
                "last_mention_days_ago":   -1,
                "mention_recency_skew":     0.0,
                "mean_sentiment":           0.0,
            })
            continue

        days_ago = (now - dates).dt.days.values
        rows.append({
            "tech_id":               tech_id,
            "first_mention_days_ago": int(days_ago.max()),
            "last_mention_days_ago":  int(days_ago.min()),
            "mention_recency_skew":   float(pd.Series(days_ago).skew()),
            "mean_sentiment":         float(sentiments.mean()) if len(sentiments) else 0.0,
        })

    df = pd.DataFrame(rows)
    logger.info("compute_article_temporal_stats: %d rows", len(df))
    return df
