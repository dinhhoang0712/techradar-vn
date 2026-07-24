package com.techpulse.techradar.features.kgreview.ports;

import reactor.core.publisher.Mono;

/**
 * Live Neo4j merges triggered from the KG review queue — mirrors the redirect-then-delete
 * pattern in {@code data-platform/gold/tech_dedup.py}'s {@code _merge_duplicate_node}, kept in
 * Java so an admin approval takes effect on the graph immediately rather than waiting for the
 * next scheduled Python job run.
 */
public interface GraphMergePort {

    /** Merges the {@code duplicateName} Technology node into {@code canonicalName}. */
    Mono<Boolean> mergeTechnology(String duplicateName, String canonicalName);

    /** Merges the {@code duplicateId} Company node into {@code canonicalId}. */
    Mono<Boolean> mergeCompany(String duplicateId, String canonicalId);
}
