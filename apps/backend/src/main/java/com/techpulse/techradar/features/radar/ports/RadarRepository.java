package com.techpulse.techradar.features.radar.ports;

import com.techpulse.techradar.features.radar.domain.RadarTrend;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.List;

/**
 * Output port for Neo4j radar queries.
 */
public interface RadarRepository {
    Flux<RadarTrend> findTopTechnologies(int limit);

    Flux<RadarTrend> findTrendsByTechnology(String technology);

    Flux<RadarTrend> searchTrends(String keyword, int limit);

    Mono<RadarTrend> findByTechnologyAndMonth(String technology, LocalDate month);
}
