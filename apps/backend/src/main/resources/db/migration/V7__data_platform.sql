-- Data Platform catalog tables.
-- Bronze: MinIO file registry.
-- Silver: processed/deduped articles and jobs.

-- ── Bronze catalog ─────────────────────────────────────────────────────────
-- Tracks every raw file written to MinIO by the Bronze Writer.
CREATE TABLE IF NOT EXISTS dp_bronze_catalog (
    id              TEXT        PRIMARY KEY,           -- MD5 of source_url
    source_url      TEXT        NOT NULL UNIQUE,
    source_platform TEXT        NOT NULL,
    content_type    TEXT        NOT NULL DEFAULT 'article', -- article | job
    minio_path      TEXT        NOT NULL,              -- s3://techradar-bronze/...
    file_size_bytes BIGINT,
    kafka_topic     TEXT,
    kafka_offset    BIGINT,
    crawled_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_dp_bronze_source   ON dp_bronze_catalog(source_platform);
CREATE INDEX IF NOT EXISTS idx_dp_bronze_crawled  ON dp_bronze_catalog(crawled_at);
CREATE INDEX IF NOT EXISTS idx_dp_bronze_type     ON dp_bronze_catalog(content_type);

-- ── Silver: processed articles ─────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS dp_processed_articles (
    id              TEXT        PRIMARY KEY,           -- MD5 of source_url
    source_url      TEXT        NOT NULL UNIQUE,
    source_platform TEXT        NOT NULL,
    title           TEXT,
    content         TEXT,
    published_at    TIMESTAMPTZ,
    crawled_at      TIMESTAMPTZ,
    bronze_path     TEXT,                             -- MinIO path của raw file
    content_hash    TEXT,                             -- MD5(title+content) cho dedup
    is_duplicate    BOOLEAN     NOT NULL DEFAULT FALSE,
    duplicate_of    TEXT,                             -- id của bài gốc nếu là duplicate
    entity_techs    TEXT[]      NOT NULL DEFAULT '{}',
    entity_orgs     TEXT[]      NOT NULL DEFAULT '{}',
    entity_locs     TEXT[]      NOT NULL DEFAULT '{}',
    quality_score   FLOAT       NOT NULL DEFAULT 0.0,
    status          TEXT        NOT NULL DEFAULT 'processed', -- processed | failed | skipped
    processed_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_dp_articles_platform  ON dp_processed_articles(source_platform);
CREATE INDEX IF NOT EXISTS idx_dp_articles_published ON dp_processed_articles(published_at);
CREATE INDEX IF NOT EXISTS idx_dp_articles_status    ON dp_processed_articles(status);
CREATE INDEX IF NOT EXISTS idx_dp_articles_hash      ON dp_processed_articles(content_hash);
CREATE INDEX IF NOT EXISTS idx_dp_articles_dedup     ON dp_processed_articles(is_duplicate);

-- ── Silver: processed job postings ─────────────────────────────────────────
CREATE TABLE IF NOT EXISTS dp_processed_jobs (
    id              TEXT        PRIMARY KEY,           -- MD5 of source_url
    source_url      TEXT        NOT NULL UNIQUE,
    source_platform TEXT        NOT NULL,
    job_title       TEXT,
    company_name    TEXT,
    company_location TEXT,
    salary          TEXT,
    level           TEXT,
    description     TEXT,
    requirement     TEXT,
    benefit         TEXT,
    skills          TEXT[]      NOT NULL DEFAULT '{}',
    technologies    TEXT[]      NOT NULL DEFAULT '{}',
    content_hash    TEXT,
    is_duplicate    BOOLEAN     NOT NULL DEFAULT FALSE,
    quality_score   FLOAT       NOT NULL DEFAULT 0.0,
    status          TEXT        NOT NULL DEFAULT 'processed',
    processed_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_dp_jobs_platform  ON dp_processed_jobs(source_platform);
CREATE INDEX IF NOT EXISTS idx_dp_jobs_company   ON dp_processed_jobs(company_name);
CREATE INDEX IF NOT EXISTS idx_dp_jobs_status    ON dp_processed_jobs(status);

-- ── Pipeline run log ───────────────────────────────────────────────────────
-- Ghi lại mỗi lần Gold ETL / enrichment chạy.
CREATE TABLE IF NOT EXISTS dp_pipeline_runs (
    id          BIGSERIAL   PRIMARY KEY,
    job_name    TEXT        NOT NULL,  -- gold_pg_etl | neo4j_enricher | embed_trigger
    status      TEXT        NOT NULL,  -- running | success | failed
    rows_affected INT,
    error_msg   TEXT,
    started_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    finished_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_dp_runs_job    ON dp_pipeline_runs(job_name);
CREATE INDEX IF NOT EXISTS idx_dp_runs_status ON dp_pipeline_runs(status);
