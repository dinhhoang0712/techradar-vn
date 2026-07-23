package com.techpulse.techradar.features.system.adapters.output;

import com.techpulse.techradar.features.system.ports.PipelineRunRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

/**
 * PostgreSQL adapter for the {@code dp_pipeline_runs} audit table that the Python data-platform
 * jobs (gold/*.py) write to on every run, on either PostgreSQL DSN the two services share.
 */
@Repository
@RequiredArgsConstructor
public class PostgresPipelineRunRepository implements PipelineRunRepository {

    private final DatabaseClient dbClient;

    @Override
    public Flux<Map<String, Object>> findLatestStatuses(List<String> jobNames) {
        String[] names = jobNames.toArray(new String[0]);
        return dbClient.sql(
                "SELECT DISTINCT ON (job_name) job_name, status, rows_affected, error_msg, "
                        + "started_at, finished_at "
                        + "FROM dp_pipeline_runs "
                        + "WHERE job_name = ANY(:names) "
                        + "ORDER BY job_name, started_at DESC")
                .bind("names", names)
                .fetch()
                .all();
    }

    @Override
    public Flux<Map<String, Object>> findRunHistory(String jobName, int limit, int offset) {
        // duration_s is null while finished_at IS NULL (still running) — interval arithmetic on a
        // null operand naturally yields null, no special-casing needed. Uses idx_dp_runs_job_started
        // (V23) to satisfy both the job_name filter and the ORDER BY ... LIMIT via index scan.
        return dbClient.sql(
                "SELECT id, job_name, status, rows_affected, error_msg, started_at, finished_at, "
                        + "EXTRACT(EPOCH FROM (finished_at - started_at)) AS duration_s "
                        + "FROM dp_pipeline_runs WHERE job_name = :jobName "
                        + "ORDER BY started_at DESC LIMIT :limit OFFSET :offset")
                .bind("jobName", jobName)
                .bind("limit", limit)
                .bind("offset", offset)
                .fetch()
                .all();
    }
}
