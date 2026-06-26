package com.techpulse.techradar.features.notification.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Domain event published to Kafka ({@code trend.alerts}) when a technology crosses the
 * month-over-month growth threshold during the radar analytics ETL. Serialized snake_case
 * by the shared Jackson {@code ObjectMapper} (e.g. {@code mom_rate}, {@code job_count}).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TrendAlertEvent {
    private String technology;
    private double momRate;
    private double growthRate;
    private int jobCount;
    private String month; // yyyy-MM
}
