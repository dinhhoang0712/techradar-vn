package com.techpulse.techradar.features.kgreview.ports;

import com.techpulse.techradar.features.kgreview.domain.CompanyDuplicateGroup;
import reactor.core.publisher.Flux;

/**
 * Detects Company near-duplicate groups live from Neo4j on demand — never persisted, mirrors
 * {@code data-platform/gold/kg_health_audit.py}'s {@code _check_company_near_duplicates}.
 */
public interface CompanyDuplicatePort {

    Flux<CompanyDuplicateGroup> detectNearDuplicates();
}
