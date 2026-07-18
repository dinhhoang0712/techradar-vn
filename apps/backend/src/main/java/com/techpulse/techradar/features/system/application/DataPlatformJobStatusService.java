package com.techpulse.techradar.features.system.application;

import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * Reads job execution history from {@code dp_pipeline_runs} — the audit table the Python
 * data-platform jobs (gold/*.py) already write to on every run, on either PostgreSQL DSN the two
 * services share. This backend never writes to that table, only reads it.
 */
@Component
@RequiredArgsConstructor
public class DataPlatformJobStatusService {

    private final DatabaseClient dbClient;

    /** Latest run (any status) per job name, newest first per group. Missing jobs are simply absent. */
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

    /** Whether the given job's latest logged run is still in-flight. */
    public Mono<Boolean> isRunning(String jobName) {
        return findLatestStatuses(List.of(jobName))
                .next()
                .map(row -> "running".equals(row.get("status")))
                .defaultIfEmpty(false);
    }
}
