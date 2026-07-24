package com.techpulse.techradar.features.kafka.ports;

import java.util.List;

/**
 * Provides the set of Company names already known in Neo4j (created via Job postings —
 * company_name comes straight from the crawler, no NLP involved). Used as a lookup dictionary
 * so {@code EntityExtractionService} can detect a Company being mentioned in Article text,
 * since it has no real NER model of its own.
 */
public interface CompanyNameProvider {

    /**
     * @return currently cached Company names (case as stored in Neo4j). May be stale by up to
     * the refresh interval of the implementation.
     */
    List<String> knownCompanyNames();
}
