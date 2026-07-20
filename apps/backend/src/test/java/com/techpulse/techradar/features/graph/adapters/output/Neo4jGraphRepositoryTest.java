package com.techpulse.techradar.features.graph.adapters.output;

import com.techpulse.techradar.shared.exception.BadRequestException;
import com.techpulse.techradar.shared.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import reactor.test.StepVerifier;

import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the Cypher-injection guards added to {@link Neo4jGraphRepository}:
 * <ul>
 *   <li>{@link Neo4jGraphRepository#findNodesByType} rejects any {@code nodeType} outside the
 *       known-label allowlist instead of splicing it straight into {@code MATCH (n:...)};</li>
 *   <li>{@link Neo4jGraphRepository#exploreNeighbors} clamps {@code depth} to the same 1..3
 *       bound {@code exploreByKeywords} already uses, instead of splicing an unbounded
 *       {@code [*1..N]-} straight into the query.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class Neo4jGraphRepositoryTest {

    @Mock
    private Driver driver;

    @Mock
    private Session session;

    @Mock
    private Result result;

    private Neo4jGraphRepository repository;

    @BeforeEach
    void setUp() {
        repository = new Neo4jGraphRepository(driver);
        lenient().when(driver.session()).thenReturn(session);
        lenient().when(result.list()).thenReturn(Collections.emptyList());
    }

    @Test
    void findNodesByType_rejectsANodeTypeNotInTheAllowlist() {
        StepVerifier.create(repository.findNodesByType("Job) DETACH DELETE n //"))
                .expectErrorSatisfies(err -> {
                    assertThat(err).isInstanceOf(BadRequestException.class);
                    assertThat(((BadRequestException) err).getErrorCode())
                            .isEqualTo(ErrorCode.INVALID_NODE_TYPE.name());
                })
                .verify();

        verify(session, never()).run(anyString());
        verify(session, never()).run(anyString(), any(Map.class));
    }

    @Test
    void findNodesByType_allowsAKnownLabelAndUsesItVerbatimInTheQuery() {
        when(session.run(contains("MATCH (n:Job)"))).thenReturn(result);

        StepVerifier.create(repository.findNodesByType("Job"))
                .verifyComplete();

        verify(session).run(contains("MATCH (n:Job)"));
    }

    @Test
    void exploreNeighbors_clampsAnOversizedDepthToThree() {
        when(session.run(anyString(), any(Map.class))).thenReturn(result);

        StepVerifier.create(repository.exploreNeighbors("42", 999))
                .verifyComplete();

        ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
        verify(session).run(queryCaptor.capture(), any(Map.class));
        assertThat(queryCaptor.getValue()).contains("[*1..3]-");
    }

    @Test
    void exploreNeighbors_clampsANegativeOrZeroDepthToOne() {
        when(session.run(anyString(), any(Map.class))).thenReturn(result);

        StepVerifier.create(repository.exploreNeighbors("42", -5))
                .verifyComplete();

        ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
        verify(session).run(queryCaptor.capture(), any(Map.class));
        assertThat(queryCaptor.getValue()).contains("[*1..1]-");
    }

    @Test
    void exploreNeighbors_passesThroughAnInRangeDepthUnchanged() {
        when(session.run(anyString(), any(Map.class))).thenReturn(result);

        StepVerifier.create(repository.exploreNeighbors("42", 2))
                .verifyComplete();

        ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
        verify(session).run(queryCaptor.capture(), any(Map.class));
        assertThat(queryCaptor.getValue()).contains("[*1..2]-");
    }
}
