package com.techpulse.techradar.features.radar.ports;

import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Output port for PostgreSQL analytics queries.
 */
public interface RadarAnalyticsRepository {
    Mono<Map<String, Object>> getAnalyticsByTechnology(String technology, LocalDate startDate, LocalDate endDate);

    Mono<List<Map<String, Object>>> getTopTechnologiesByJobCount(int limit, LocalDate month);

    Mono<List<Map<String, Object>>> getGrowthTrends(String technology);
}
