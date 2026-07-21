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
}
