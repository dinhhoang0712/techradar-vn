package com.techpulse.techradar.features.radar.ports;

import com.techpulse.techradar.features.radar.domain.TechAnalyticsRow;
import reactor.core.publisher.Mono;

/**
 * Write port for persisting one {@code tech_analytics} row (upsert), used by the ETL rebuild.
 */
public interface TechAnalyticsWritePort {

    Mono<Long> upsert(TechAnalyticsRow row);
}
