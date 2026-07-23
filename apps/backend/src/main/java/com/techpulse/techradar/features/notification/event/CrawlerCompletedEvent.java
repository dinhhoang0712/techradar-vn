package com.techpulse.techradar.features.notification.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Domain event published over Redis Pub/Sub ({@code crawler:completed}) by
 * {@code services/crawler/run_all.py} at the end of every crawl run (scheduled or
 * admin-triggered). Serialized snake_case by the shared Jackson {@code ObjectMapper} (e.g.
 * {@code success_count}).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CrawlerCompletedEvent {
    private Integer successCount;
    private Integer total;
}
