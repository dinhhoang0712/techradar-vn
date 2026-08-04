"""
Gold — Neo4j Job/Company Sync
Đọc dp_processed_jobs (Postgres, silver layer) → MERGE Job/Company node +
REQUIRES/POSTED_BY relationship vào Neo4j.

Job này lấp khoảng trống: KafkaNeo4jWriterService (Java) chỉ ghi Job/Company
khi tin tuyển dụng đi qua trót lọt Kafka topic raw_jobs → extracted_jobs
(crawler → KafkaExtractorService). Crawler job (TopCV/ITviec/TopDev, chạy
Selenium) hay fail, và topic Kafka không có volume nên mất data khi container
restart — job đã crawl vẫn nằm nguyên trong dp_processed_jobs nhưng không bao
giờ tới graph. Job này đồng bộ trực tiếp từ Postgres, độc lập với Kafka, dùng
chung id scheme (md5(source_url) cho Job, slugify(name) cho Company) với
KafkaNeo4jWriterService để hai đường ghi không tạo node trùng.

Chạy: mỗi giờ, trước gold_pg_etl (cấu hình trong scheduler).
"""

from __future__ import annotations

import hashlib
import re

from common.db import get_neo4j_driver, get_pg_conn, log_pipeline_run
from common.tech_keywords import extract_tech
from config import Settings
from loguru import logger

_SELECT_JOBS = """
SELECT id, source_url, source_platform, job_title, company_name,
       company_location, salary, level, description, requirement, benefit,
       skills, technologies, company_industry, company_size
FROM dp_processed_jobs
WHERE is_duplicate = false AND status = 'processed'
"""

_MERGE_JOBS = """
UNWIND $rows AS row
MERGE (j:Job {id: row.id})
SET j.name = row.job_title,
    j.description = row.description,
    j.requirement = row.requirement,
    j.benefit = row.benefit,
    j.salary = row.salary,
    j.level = row.level,
    j.url = row.source_url,
    j.source_platform = row.source_platform
"""

_MERGE_COMPANIES = """
UNWIND $rows AS row
MERGE (c:Company {id: row.company_id})
SET c.name = row.company_name,
    c.location = row.company_location,
    c.industry = CASE WHEN row.company_industry IS NULL OR row.company_industry = ''
                 THEN c.industry ELSE row.company_industry END,
    c.size = CASE WHEN row.company_size IS NULL OR row.company_size = ''
             THEN c.size ELSE row.company_size END
WITH row
MATCH (j:Job {id: row.id})
MATCH (c:Company {id: row.company_id})
MERGE (j)-[:POSTED_BY]->(c)
"""

_MERGE_TECHS = """
UNWIND $rows AS row
UNWIND row.technologies AS tech
MATCH (j:Job {id: row.id})
MERGE (t:Technology {name: tech})
MERGE (j)-[:REQUIRES]->(t)
ON CREATE SET t.mention_count = coalesce(t.mention_count, 0) + 1
"""

_MERGE_SKILLS = """
UNWIND $rows AS row
UNWIND row.skills AS skill
MATCH (j:Job {id: row.id})
MERGE (s:Skill {name: skill})
SET s.mention_count = coalesce(s.mention_count, 0) + 1
MERGE (j)-[:REQUIRES]->(s)
"""


def _slugify(value: str) -> str:
    value = (value or "").strip().lower()
    value = re.sub(r"[^a-z0-9]+", "-", value)
    return value.strip("-")


def _job_id(source_url: str) -> str:
    return hashlib.md5((source_url or "").encode("utf-8")).hexdigest()


def run(settings: Settings) -> int:
    logger.info("Neo4j Job Sync: starting...")

    pg_conn = get_pg_conn(settings)
    run_id = log_pipeline_run(pg_conn, "neo4j_job_sync", "running")

    try:
        with pg_conn.cursor() as cur:
            cur.execute(_SELECT_JOBS)
            jobs = cur.fetchall()

        rows = []
        rows_with_company = []
        for job in jobs:
            techs = set(job["technologies"] or [])
            techs |= set(
                extract_tech(f"{job['job_title'] or ''} {job['description'] or ''} {job['requirement'] or ''}")
            )

            row = {
                "id": _job_id(job["source_url"]),
                "source_platform": job["source_platform"],
                "job_title": job["job_title"],
                "description": job["description"],
                "requirement": job["requirement"],
                "benefit": job["benefit"],
                "salary": job["salary"],
                "level": job["level"],
                "source_url": job["source_url"],
                "technologies": sorted(techs),
                "skills": job["skills"] or [],
            }
            rows.append(row)

            company_name = (job["company_name"] or "").strip()
            if company_name:
                rows_with_company.append(
                    {
                        **row,
                        "company_id": _slugify(company_name),
                        "company_name": company_name,
                        "company_location": job["company_location"],
                        "company_industry": job["company_industry"],
                        "company_size": job["company_size"],
                    }
                )

        driver = get_neo4j_driver(settings)
        with driver.session() as session:
            if rows:
                session.run(_MERGE_JOBS, rows=rows)
                session.run(_MERGE_TECHS, rows=rows)
                session.run(_MERGE_SKILLS, rows=rows)
            if rows_with_company:
                session.run(_MERGE_COMPANIES, rows=rows_with_company)
        driver.close()

        logger.info("Neo4j Job Sync: upserted {} jobs ({} with company)", len(rows), len(rows_with_company))
        log_pipeline_run(pg_conn, "neo4j_job_sync", "success", rows_affected=len(rows), run_id=run_id)
        return len(rows)

    except Exception as exc:
        logger.exception("Neo4j Job Sync failed")
        try:
            log_pipeline_run(pg_conn, "neo4j_job_sync", "failed", error_msg=str(exc), run_id=run_id)
        except Exception:
            pass
        raise
    finally:
        pg_conn.close()
