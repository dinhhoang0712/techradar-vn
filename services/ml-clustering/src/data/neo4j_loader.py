"""
Đọc đồ thị tri thức từ Neo4j AuraDB và xuất ra parquet (làm input cho stage features).

Bối cảnh thực tế (đã inspect AuraDB ngày 06/05/2026):
  - 1,137 :Technology, 834 :Article (526 đã có embedding 768d), 1,917 :Company,
    380 :Job, 582 :Skill.
  - Cạnh chính dùng cho clustering:
      (Article)-[:MENTIONS]->(Technology)        15,957
      (Company)-[:USES]->(Technology)            11,296
      (Job)-[:REQUIRES]->(Technology)             4,916
      (Job)-[:REQUIRES]->(Skill)                  3,596
      (Technology)-[:RELATED_TO]->(Technology)       70
      (Skill)-[:IS_TECHNOLOGY]->(Technology)         22
  - GDS plugin sẵn sàng (393 procs), APOC v2026.04.0 sẵn sàng → có thể chiếu
    đồ thị in-memory thẳng trên cloud, KHÔNG cần ETL về local.

Quy ước:
  - `tech_id` chính = `elementId(t)` (string). Bền hơn `name` (có thể trùng
    sau lower-case / trimming, ví dụ "Apache Spark" và "Spark").
  - Mọi Cypher đều parameterized; không nối f-string vào câu lệnh.
  - Driver dùng singleton ở module này; gọi `close_driver()` cuối stage.
"""

from __future__ import annotations

import logging
import warnings
from pathlib import Path
from typing import Mapping, Sequence

import pandas as pd
from neo4j import GraphDatabase, Driver

from conf.config import get_settings

logger = logging.getLogger(__name__)

# Singleton driver — chỉ tạo 1 lần, dùng chung toàn module
_driver: Driver | None = None


# ---------------------------------------------------------------------------
# Connection
# ---------------------------------------------------------------------------

def get_driver() -> Driver:
    """
    Trả về Neo4j sync `Driver` đã được khởi tạo + cache module-level.
    Singleton: chỉ tạo lần đầu, các lần sau trả về cùng instance.
    """
    global _driver
    if _driver is None:
        s = get_settings()
        _driver = GraphDatabase.driver(
            s.active_neo4j_uri,
            auth=(s.active_neo4j_username, s.active_neo4j_password),
            max_connection_pool_size=50,
        )
        logger.info("Neo4j driver created: %s", s.active_neo4j_uri)
    return _driver


def close_driver() -> None:
    """Đóng driver singleton nếu đang mở."""
    global _driver
    if _driver is not None:
        _driver.close()
        _driver = None
        logger.info("Neo4j driver closed.")


def run_query(cypher: str, params: Mapping | None = None) -> list[dict]:
    """
    Helper chạy 1 câu Cypher → list dict (kiểu `result.data()`).
    Dùng session ngắn hạn, không bắt nuốt exception.
    """
    with get_driver().session() as session:
        result = session.run(cypher, parameters=dict(params or {}))
        return result.data()


# ---------------------------------------------------------------------------
# Node fetchers
# ---------------------------------------------------------------------------

def fetch_technologies(min_degree: int = 1) -> pd.DataFrame:
    """
    Chỉ lấy :Technology có ít nhất min_degree Job -[:REQUIRES]-> tech.
    Cột: tech_id, name, category, description, trend_score, degree_total.
    """
    rows = run_query(
        """
        MATCH (t:Technology)<-[:REQUIRES]-(j:Job)
        WITH t, count(j) AS job_count
        WHERE job_count >= $min_degree
        RETURN
            elementId(t)        AS tech_id,
            t.name              AS name,
            t.category          AS category,
            t.description       AS description,
            coalesce(t.trend_score, 0.0) AS trend_score,
            job_count                    AS degree_total
        ORDER BY job_count DESC
        """,
        {"min_degree": min_degree},
    )
    df = pd.DataFrame(rows)
    logger.info("fetch_technologies: %d rows", len(df))
    return df


def fetch_companies() -> pd.DataFrame:
    """
    Lấy toàn bộ :Company.
    Cột: company_id, name, industry, location, size, rating.
    """
    rows = run_query(
        """
        MATCH (c:Company)
        RETURN
            elementId(c)                    AS company_id,
            c.name                          AS name,
            coalesce(c.industry, "")        AS industry,
            coalesce(c.location, "")        AS location,
            coalesce(c.size, "")            AS size,
            coalesce(c.rating, 0.0)         AS rating
        """
    )
    df = pd.DataFrame(rows)
    logger.info("fetch_companies: %d rows", len(df))
    return df


def fetch_articles(only_with_embedding: bool = True) -> pd.DataFrame:
    """
    Lấy :Article. Nếu only_with_embedding=True chỉ lấy bài đã có embedding.
    Cột: article_id, title, published_date, sentiment_score, embedding.
    embedding trả dạng list[float] — caller sẽ stack thành np.ndarray.
    """
    where = "WHERE a.embedding IS NOT NULL" if only_with_embedding else ""
    rows = run_query(
        f"""
        MATCH (a:Article)
        {where}
        RETURN
            elementId(a)                        AS article_id,
            coalesce(a.title, "")               AS title,
            coalesce(a.published_date, "")      AS published_date,
            coalesce(a.sentiment_score, 0.0)    AS sentiment_score,
            a.embedding                         AS embedding
        """
    )
    if not rows:
        logger.info("fetch_articles: 0 rows — DB chưa có Article.")
        return pd.DataFrame(columns=["article_id", "title", "published_date", "sentiment_score", "embedding"])

    df = pd.DataFrame(rows)

    n_missing = df["embedding"].isna().sum()
    if n_missing > 0:
        warnings.warn(
            f"fetch_articles: {n_missing}/{len(df)} bài thiếu embedding — sẽ bị drop ở content_features.",
            stacklevel=2,
        )
    logger.info("fetch_articles: %d rows (only_with_embedding=%s)", len(df), only_with_embedding)
    return df


def fetch_jobs() -> pd.DataFrame:
    """
    Lấy :Job.
    Cột: job_id, title, level, salary, posted_date, source_url, company_name.
    """
    rows = run_query(
        """
        MATCH (j:Job)
        RETURN
            elementId(j)                    AS job_id,
            coalesce(j.title, "")           AS title,
            coalesce(j.level, "")           AS level,
            coalesce(j.salary, "")          AS salary,
            coalesce(j.posted_date, "")     AS posted_date,
            coalesce(j.source_url, "")      AS source_url,
            coalesce(j.company_name, "")    AS company_name
        """
    )
    df = pd.DataFrame(rows)
    logger.info("fetch_jobs: %d rows", len(df))
    return df


def fetch_skills() -> pd.DataFrame:
    """
    Lấy :Skill.
    Cột: skill_id, name, category, demand_score.
    """
    rows = run_query(
        """
        MATCH (s:Skill)
        RETURN
            elementId(s)                    AS skill_id,
            s.name                          AS name,
            coalesce(s.category, "")        AS category,
            coalesce(s.demand_score, 0.0)   AS demand_score
        """
    )
    df = pd.DataFrame(rows)
    logger.info("fetch_skills: %d rows", len(df))
    return df


# ---------------------------------------------------------------------------
# Edge fetchers
# ---------------------------------------------------------------------------

def fetch_edges_article_mentions_tech() -> pd.DataFrame:
    """Cột: article_id, tech_id."""
    rows = run_query(
        """
        MATCH (a:Article)-[:MENTIONS]->(t:Technology)
        RETURN elementId(a) AS article_id, elementId(t) AS tech_id
        """
    )
    df = pd.DataFrame(rows)
    logger.info("fetch_edges_article_mentions_tech: %d rows", len(df))
    return df


def fetch_edges_company_uses_tech() -> pd.DataFrame:
    """Cột: company_id, tech_id."""
    rows = run_query(
        """
        MATCH (c:Company)-[:USES]->(t:Technology)
        RETURN elementId(c) AS company_id, elementId(t) AS tech_id
        """
    )
    df = pd.DataFrame(rows)
    logger.info("fetch_edges_company_uses_tech: %d rows", len(df))
    return df


def fetch_edges_job_requires_tech() -> pd.DataFrame:
    """Cột: job_id, tech_id."""
    rows = run_query(
        """
        MATCH (j:Job)-[:REQUIRES]->(t:Technology)
        RETURN elementId(j) AS job_id, elementId(t) AS tech_id
        """
    )
    df = pd.DataFrame(rows)
    logger.info("fetch_edges_job_requires_tech: %d rows", len(df))
    return df


def fetch_edges_job_requires_skill() -> pd.DataFrame:
    """Cột: job_id, skill_id."""
    rows = run_query(
        """
        MATCH (j:Job)-[:REQUIRES]->(s:Skill)
        RETURN elementId(j) AS job_id, elementId(s) AS skill_id
        """
    )
    df = pd.DataFrame(rows)
    logger.info("fetch_edges_job_requires_skill: %d rows", len(df))
    return df


def fetch_edges_tech_related_tech() -> pd.DataFrame:
    """
    Cột: tech_id_a, tech_id_b.
    Xuất 2 chiều (vô hướng) — mỗi cạnh thành 2 row để feature đối xứng.
    """
    rows = run_query(
        """
        MATCH (a:Technology)-[:RELATED_TO]->(b:Technology)
        RETURN elementId(a) AS tech_id_a, elementId(b) AS tech_id_b
        UNION
        MATCH (a:Technology)-[:RELATED_TO]->(b:Technology)
        RETURN elementId(b) AS tech_id_a, elementId(a) AS tech_id_b
        """
    )
    df = pd.DataFrame(rows).drop_duplicates()
    logger.info("fetch_edges_tech_related_tech: %d rows", len(df))
    return df


def fetch_edges_skill_is_technology() -> pd.DataFrame:
    """Cột: skill_id, tech_id."""
    rows = run_query(
        """
        MATCH (s:Skill)-[:IS_TECHNOLOGY]->(t:Technology)
        RETURN elementId(s) AS skill_id, elementId(t) AS tech_id
        """
    )
    df = pd.DataFrame(rows)
    logger.info("fetch_edges_skill_is_technology: %d rows", len(df))
    return df


# ---------------------------------------------------------------------------
# GDS — projection / cleanup
# ---------------------------------------------------------------------------

def project_gds_graph(
    driver: Driver,
    graph_name: str,
    include_relations: Sequence[str] = (
        "MENTIONS", "USES", "REQUIRES", "RELATED_TO", "IS_TECHNOLOGY",
    ),
) -> dict:
    """
    Tạo named graph projection trong GDS (heterogeneous — nhiều label).
    Nếu graph_name đã tồn tại → drop trước (idempotent).
    Trả về {nodeCount, relationshipCount, projectMillis}.
    """
    # Drop nếu đã tồn tại
    drop_gds_graph(driver, graph_name)

    rel_types = "|".join(include_relations)
    with driver.session() as session:
        result = session.run(
            """
            CALL gds.graph.project(
                $graph_name,
                ['Technology','Article','Company','Job','Skill'],
                {
                    MENTIONS:       {orientation: 'UNDIRECTED'},
                    USES:           {orientation: 'UNDIRECTED'},
                    REQUIRES:       {orientation: 'UNDIRECTED'},
                    RELATED_TO:     {orientation: 'UNDIRECTED'},
                    IS_TECHNOLOGY:  {orientation: 'UNDIRECTED'}
                }
            )
            YIELD nodeCount, relationshipCount, projectMillis
            """,
            {"graph_name": graph_name},
        )
        info = result.single()
        meta = dict(info) if info else {}
    logger.info("GDS projection '%s': %s", graph_name, meta)
    return meta


def drop_gds_graph(driver: Driver, graph_name: str) -> None:
    """Drop named graph nếu tồn tại. Không raise nếu không có."""
    with driver.session() as session:
        exists = session.run(
            "CALL gds.graph.exists($name) YIELD exists",
            {"name": graph_name},
        ).single()
        if exists and exists["exists"]:
            session.run(
                "CALL gds.graph.drop($name)",
                {"name": graph_name},
            )
            logger.info("GDS graph '%s' dropped.", graph_name)


# ---------------------------------------------------------------------------
# IO helpers
# ---------------------------------------------------------------------------

def save_parquet(df: pd.DataFrame, path: str | Path) -> Path:
    """Ghi DataFrame ra parquet (snappy). Tạo parent dir nếu thiếu."""
    p = Path(path).resolve()
    p.parent.mkdir(parents=True, exist_ok=True)
    df.to_parquet(p, compression="snappy", index=False)
    logger.info("Saved %d rows → %s", len(df), p)
    return p


def load_parquet(path: str | Path) -> pd.DataFrame:
    """Đọc parquet. Raise FileNotFoundError nếu thiếu."""
    p = Path(path).resolve()
    if not p.exists():
        raise FileNotFoundError(f"Parquet not found: {p}")
    return pd.read_parquet(p)
