package com.techpulse.techradar.features.notification.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Domain event published to Kafka ({@code roadmap.alerts}) when a user's #1 recommended next
 * skill (from {@code GetCareerRoadmapUseCase}, via the weekly {@code RoadmapAlertService} scan) is
 * growing fast. Unlike {@link TrendAlertEvent} (fanned out to every subscriber of one technology),
 * this event already targets exactly one user — the scan resolves the recipient and their
 * delivery preferences up front, so the dispatcher does no repository lookup. Serialized
 * snake_case (e.g. {@code user_id}, {@code growth_rate}).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RoadmapAlertEvent {
    private String userId;
    private String email;
    private boolean notifyInapp;
    private boolean notifyEmail;
    private String technology;
    private double growthRate;
}
