package com.techpulse.techradar.features.notification.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Domain event published over Redis Pub/Sub ({@code job:completed}) by
 * {@code data-platform/common/db.py}'s {@code log_pipeline_run} whenever a data-platform gold job
 * (article_sync, job_sync, gold_pg_etl, enricher, tech_dedup, embed_trigger) reaches a terminal
 * status. Serialized snake_case by the shared Jackson {@code ObjectMapper} (e.g. {@code job_name},
 * {@code rows_affected}).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class JobCompletedEvent {
    private String jobName;
    private String status;
    private Integer rowsAffected;
    private String errorMsg;
}
