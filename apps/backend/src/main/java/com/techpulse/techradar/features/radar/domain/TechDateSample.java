package com.techpulse.techradar.features.radar.domain;

/**
 * One raw (technology, date) observation pulled from the knowledge graph — an article mention or
 * a job posting, not yet bucketed into a month. {@code rawDate} is unparsed on purpose: date
 * strings mix ISO and dd/MM/yyyy vs. MM/dd/yyyy formats depending on crawler source, and
 * {@link FlexibleDateParser} (not Cypher) is responsible for disambiguating them.
 */
public record TechDateSample(String tech, String rawDate) {
}
