package com.techpulse.techradar.features.system.ports;

import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

/**
 * Persistence port for reading job execution history from {@code dp_pipeline_runs} — the audit
 * table the Python data-platform jobs (gold/*.py) write to on every run. This backend never
 * writes to that table, only reads it.
 */
public interface PipelineRunRepository {

    /** Latest run (any status) per job name, newest first per group. Missing jobs are simply absent. */
    Flux<Map<String, Object>> findLatestStatuses(List<String> jobNames);
}
