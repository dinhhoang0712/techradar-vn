package com.techpulse.techradar.features.compare.ports;

import com.techpulse.techradar.features.compare.domain.TechComparison;
import reactor.core.publisher.Mono;

/**
 * Output port for comparing technologies in Neo4j.
 */
public interface CompareRepository {
    Mono<TechComparison> compareTechnologies(String tech1, String tech2);
}
