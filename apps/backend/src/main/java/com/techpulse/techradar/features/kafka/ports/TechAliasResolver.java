package com.techpulse.techradar.features.kafka.ports;

/**
 * Resolves a raw technology name to its canonical form, backed by {@code dp_tech_alias_map}
 * (shared with silver/processor.py in data-platform) so "Go"/"Golang", "ML"/"Machine Learning"...
 * don't split into separate Technology nodes in Neo4j.
 */
public interface TechAliasResolver {

    /**
     * @return the canonical name if {@code rawName} matches a known alias (case-insensitive,
     * whitespace-stripped); otherwise the stripped original name.
     */
    String resolve(String rawName);
}
