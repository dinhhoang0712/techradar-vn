package com.techpulse.techradar.features.radar.ports;

import com.techpulse.techradar.features.radar.domain.MonthlyCount;
import com.techpulse.techradar.features.radar.domain.TechSnapshot;
import reactor.core.publisher.Flux;

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
}
