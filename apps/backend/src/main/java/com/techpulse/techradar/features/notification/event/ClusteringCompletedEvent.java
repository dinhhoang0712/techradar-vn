package com.techpulse.techradar.features.notification.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Domain event published over Redis Pub/Sub ({@code clustering:completed}) by
 * {@code services/ml-clustering/app/routes_pipeline.py} at the end of every retrain run
 * (scheduled or admin-triggered). Serialized snake_case by the shared Jackson
 * {@code ObjectMapper} (e.g. {@code duration_s}, {@code snapshot_tag}).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClusteringCompletedEvent {
    /** {@code "success"} or {@code "failed"}. */
    private String status;
    private Integer durationS;
    private String snapshotTag;
    /** Only set when {@code status} is {@code "failed"}. */
    private String error;
}
