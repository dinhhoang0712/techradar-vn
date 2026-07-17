-- Company industry/headcount, scraped by some crawlers (e.g. TopCV "Lĩnh vực"/"Quy mô") but
-- previously dropped before reaching the silver layer. Additive/nullable — existing rows are
-- unaffected; only newly (re-)processed job postings will populate these.
ALTER TABLE dp_processed_jobs
    ADD COLUMN IF NOT EXISTS company_industry TEXT,
    ADD COLUMN IF NOT EXISTS company_size     TEXT;
