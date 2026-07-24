package com.techpulse.techradar.features.kgreview.adapters.output;

import com.techpulse.techradar.features.kgreview.ports.GraphMergePort;
import lombok.RequiredArgsConstructor;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.neo4j.driver.Value;
import org.neo4j.driver.Values;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;

/**
 * Live Neo4j merges for the KG review queue — same redirect-then-DETACH-DELETE pattern as
 * {@code data-platform/gold/tech_dedup.py}'s {@code _merge_duplicate_node}/
 * {@code _merge_duplicate_node_by_id}, reimplemented in Java so an admin approval takes effect
 * immediately rather than waiting for the next scheduled Python job.
 * <p>
 * Blocking Neo4j Java driver calls, bridged onto a boundedElastic scheduler — same pattern as
 * {@link com.techpulse.techradar.features.graph.adapters.output.Neo4jGraphAnalyticsAdapter}.
 */
@Component
@RequiredArgsConstructor
public class Neo4jGraphMergeAdapter implements GraphMergePort {

    /** Relationship types Technology can be the TARGET of (redirect incoming). */
    private static final List<String> TECH_INCOMING_REL_TYPES = List.of("MENTIONS", "REQUIRES", "USES", "IS_TECHNOLOGY");
    /** Relationship types Technology can be the SOURCE of, besides RELATED_TO (redirect outgoing). */
    private static final List<String> TECH_OUTGOING_REL_TYPES = List.of("BELONGS_TO", "NEAR_CLUSTER");

    /** Relationship types Company can be the TARGET of (redirect incoming). */
    private static final List<String> COMPANY_INCOMING_REL_TYPES = List.of("MENTIONS", "POSTED_BY", "HIRES_FOR");
    /** Relationship types Company can be the SOURCE of (redirect outgoing). */
    private static final List<String> COMPANY_OUTGOING_REL_TYPES = List.of("USES");

    private final Driver driver;

    @Override
    public Mono<Boolean> mergeTechnology(String duplicateName, String canonicalName) {
        return Mono.fromCallable(() -> mergeByProperty(
                        "Technology", "name", duplicateName, canonicalName,
                        TECH_INCOMING_REL_TYPES, TECH_OUTGOING_REL_TYPES, true))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Boolean> mergeCompany(String duplicateId, String canonicalId) {
        return Mono.fromCallable(() -> mergeByProperty(
                        "Company", "id", duplicateId, canonicalId,
                        COMPANY_INCOMING_REL_TYPES, COMPANY_OUTGOING_REL_TYPES, false))
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Redirects every known relationship type off {@code (label {key: duplicateValue})} onto
     * {@code (label {key: canonicalValue})}, then {@code DETACH DELETE}s the duplicate.
     *
     * @param includeRelatedTo also redirect the bidirectional {@code RELATED_TO} self-relationship
     *                         (Technology-to-Technology co-mention) — Company has no such edge.
     * @return false if the two nodes don't both exist (or resolve to the same node) — no-op, safe
     *         to call repeatedly.
     */
    private boolean mergeByProperty(
            String label, String key, String duplicateValue, String canonicalValue,
            List<String> incomingRelTypes, List<String> outgoingRelTypes, boolean includeRelatedTo
    ) {
        try (Session session = driver.session()) {
            long exists = session.run(
                    "MATCH (canonical:" + label + " {" + key + ": $canonical}) " +
                            "MATCH (dup:" + label + " {" + key + ": $dup}) " +
                            "WHERE elementId(canonical) <> elementId(dup) " +
                            "RETURN count(*) AS c",
                    Values.parameters("canonical", canonicalValue, "dup", duplicateValue)
            ).single().get("c").asLong();
            if (exists == 0) {
                return false;
            }

            Value params = Values.parameters("canonical", canonicalValue, "dup", duplicateValue);

            for (String relType : incomingRelTypes) {
                session.run(
                        "MATCH (other)-[r:" + relType + "]->(dup:" + label + " {" + key + ": $dup}) " +
                                "MATCH (canonical:" + label + " {" + key + ": $canonical}) " +
                                "WHERE elementId(canonical) <> elementId(dup) " +
                                "MERGE (other)-[:" + relType + "]->(canonical) " +
                                "DELETE r",
                        params
                ).consume();
            }

            for (String relType : outgoingRelTypes) {
                session.run(
                        "MATCH (dup:" + label + " {" + key + ": $dup})-[r:" + relType + "]->(other) " +
                                "MATCH (canonical:" + label + " {" + key + ": $canonical}) " +
                                "WHERE elementId(canonical) <> elementId(dup) " +
                                "MERGE (canonical)-[:" + relType + "]->(other) " +
                                "DELETE r",
                        params
                ).consume();
            }

            if (includeRelatedTo) {
                session.run(
                        "MATCH (dup:" + label + " {" + key + ": $dup})-[r:RELATED_TO]->(other) " +
                                "MATCH (canonical:" + label + " {" + key + ": $canonical}) " +
                                "WHERE elementId(canonical) <> elementId(dup) AND elementId(other) <> elementId(canonical) " +
                                "MERGE (canonical)-[:RELATED_TO]->(other) " +
                                "DELETE r",
                        params
                ).consume();
                session.run(
                        "MATCH (other)-[r:RELATED_TO]->(dup:" + label + " {" + key + ": $dup}) " +
                                "MATCH (canonical:" + label + " {" + key + ": $canonical}) " +
                                "WHERE elementId(canonical) <> elementId(dup) AND elementId(other) <> elementId(canonical) " +
                                "MERGE (other)-[:RELATED_TO]->(canonical) " +
                                "DELETE r",
                        params
                ).consume();
            }

            session.run(
                    "MATCH (dup:" + label + " {" + key + ": $dup}) DETACH DELETE dup",
                    Values.parameters("dup", duplicateValue)
            ).consume();
            return true;
        }
    }
}
