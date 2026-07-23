package com.techpulse.techradar.features.radar.ports;

import com.techpulse.techradar.features.radar.domain.MonthlyCount;
import com.techpulse.techradar.features.radar.domain.TechSnapshot;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Read port for time-series technology analytics backing the radar &amp; compare features.
 * <p>
 * Backed by the {@code tech_analytics} Postgres table (the intended time-series store). An ETL job
 * is expected to populate it from the knowledge graph; until then these queries return empty results
 * with the correct shape.
 */
public interface RadarQueryRepository {

    /**
     * Top technologies by the most recent month's job count.
     */
    Flux<TechSnapshot> topTechnologies(int limit);

    /**
     * Monthly counts for the given technology names within the last {@code months} months.
     */
    Flux<MonthlyCount> monthlySeries(List<String> keywords, int months);

    /**
     * Technologies whose earliest {@code tech_analytics} row is the current calendar month — i.e.
     * first tracked this month. Backs the admin live-metrics dashboard's "new technologies" count.
     */
    Mono<Long> countNewTechnologiesThisMonth();

    /**
     * Latest {@code tech_analytics} snapshot for each of the given technology names (already
     * lower-cased by the caller). Names with no tracked row are simply absent from the result —
     * backs the Company Tech Health Score, which averages only over technologies with real data.
     */
    Flux<TechSnapshot> findLatestSnapshotsForNames(List<String> namesLower);
}
