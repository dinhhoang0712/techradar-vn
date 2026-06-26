package com.techpulse.techradar.features.system.ports;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Output port for the {@code activity_log} table backing the admin dashboard metrics.
 */
public interface ActivityLogRepository {

    Mono<Void> recordVisit(String userId, String path);

    Mono<Void> recordSearch(String keyword);

    Mono<Long> countToday(String type);

    /** Visits grouped by month: {@code [{month: "YYYY-MM", count: n}, ...]} (last 12 months). */
    Flux<Map<String, Object>> monthlyVisits();

    /** Most frequent search keywords. */
    Flux<String> topKeywords(int limit);
}
