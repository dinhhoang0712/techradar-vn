package com.techpulse.techradar.features.system.application;

import com.techpulse.techradar.features.system.ports.PipelineRunRepository;
import lombok.RequiredArgsConstructor;
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

    private final PipelineRunRepository pipelineRunRepository;

    /** Latest run (any status) per job name, newest first per group. Missing jobs are simply absent. */
    public Flux<Map<String, Object>> findLatestStatuses(List<String> jobNames) {
        return pipelineRunRepository.findLatestStatuses(jobNames);
    }

    /** Whether the given job's latest logged run is still in-flight. */
    public Mono<Boolean> isRunning(String jobName) {
        return findLatestStatuses(List.of(jobName))
                .next()
                .map(row -> "running".equals(row.get("status")))
                .defaultIfEmpty(false);
    }

    /** Full run history for one job, newest first, paginated. */
    public Flux<Map<String, Object>> findRunHistory(String jobName, int limit, int offset) {
        return pipelineRunRepository.findRunHistory(jobName, limit, offset);
    }
}
