-- History queries filter by job_name and sort by started_at DESC — the existing idx_dp_runs_job
-- alone means Postgres still has to sort matches on the fly; this composite index lets it satisfy
-- both the filter and the ORDER BY ... LIMIT via index scan.
CREATE INDEX IF NOT EXISTS idx_dp_runs_job_started ON dp_pipeline_runs(job_name, started_at DESC);
