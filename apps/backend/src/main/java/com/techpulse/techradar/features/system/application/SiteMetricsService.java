package com.techpulse.techradar.features.system.application;

import com.techpulse.techradar.features.system.ports.ActivityLogRepository;
import com.techpulse.techradar.features.user.application.AdminUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * Site-wide traffic metrics for the admin dashboard: registered users and the
 * {@code activity_log} visit/search counters recorded by {@code ActivityTrackingFilter}.
 */
@Component
@RequiredArgsConstructor
public class SiteMetricsService {

    private static final int TOP_N = 10;

    private final AdminUserService userService;
    private final ActivityLogRepository activityLog;

    public Mono<Long> userCount() {
        return userService.listUsers().count();
    }

    public Mono<Long> visitsToday() {
        return activityLog.countToday("visit");
    }

    public Mono<Long> searchesToday() {
        return activityLog.countToday("search");
    }

    public Mono<List<Map<String, Object>>> monthlyVisits() {
        return activityLog.monthlyVisits().collectList();
    }

    public Mono<List<String>> topKeywords() {
        return activityLog.topKeywords(TOP_N).collectList();
    }
}
