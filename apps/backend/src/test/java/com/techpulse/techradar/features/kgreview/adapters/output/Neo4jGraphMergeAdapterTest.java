package com.techpulse.techradar.features.kgreview.adapters.output;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.Value;
import reactor.test.StepVerifier;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the redirect-then-DETACH-DELETE Cypher pattern for {@link Neo4jGraphMergeAdapter} —
 * same shape as {@code data-platform/gold/tech_dedup.py}'s {@code _merge_duplicate_node}, kept
 * in sync deliberately (see that file for the Python side of the same contract).
 */
@ExtendWith(MockitoExtension.class)
class Neo4jGraphMergeAdapterTest {

    @Mock
    private Driver driver;
    @Mock
    private Session session;
    @Mock
    private Result existsResult;
    @Mock
    private Record existsRecord;
    @Mock
    private Value countValue;

    private Neo4jGraphMergeAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new Neo4jGraphMergeAdapter(driver);
        lenient().when(driver.session()).thenReturn(session);
    }

    private void stubExistenceCheck(long count) {
        when(session.run(contains("RETURN count(*)"), any(Value.class))).thenReturn(existsResult);
        when(existsResult.single()).thenReturn(existsRecord);
        when(existsRecord.get("c")).thenReturn(countValue);
        when(countValue.asLong()).thenReturn(count);
    }

    @Test
    void mergeTechnology_bothNodesExist_redirectsAllKnownTypesThenDeletes() {
        stubExistenceCheck(1);
        Result genericResult = mock(Result.class);
        lenient().when(session.run(anyString(), any(Value.class))).thenReturn(genericResult);
        when(session.run(contains("RETURN count(*)"), any(Value.class))).thenReturn(existsResult);

        StepVerifier.create(adapter.mergeTechnology("Golang", "Go"))
                .expectNext(true)
                .verifyComplete();

        ArgumentCaptor<String> cypherCaptor = ArgumentCaptor.forClass(String.class);
        verify(session, atLeastOnce()).run(cypherCaptor.capture(), any(Value.class));
        List<String> cyphers = cypherCaptor.getAllValues();

        // existence check + 4 incoming (MENTIONS/REQUIRES/USES/IS_TECHNOLOGY)
        // + 2 outgoing (BELONGS_TO/NEAR_CLUSTER) + 2 RELATED_TO directions + DETACH DELETE = 10.
        assertThat(cyphers).hasSize(10);
        assertThat(cyphers).anyMatch(c -> c.contains("MENTIONS"));
        assertThat(cyphers).anyMatch(c -> c.contains("REQUIRES"));
        assertThat(cyphers).anyMatch(c -> c.contains("USES"));
        assertThat(cyphers).anyMatch(c -> c.contains("IS_TECHNOLOGY"));
        assertThat(cyphers).anyMatch(c -> c.contains("BELONGS_TO"));
        assertThat(cyphers).anyMatch(c -> c.contains("NEAR_CLUSTER"));
        assertThat(cyphers).filteredOn(c -> c.contains("RELATED_TO")).hasSize(2);
        assertThat(cyphers.get(cyphers.size() - 1)).contains("DETACH DELETE");
    }

    @Test
    void mergeTechnology_duplicateDoesNotExist_returnsFalseAndRunsOnlyTheExistenceCheck() {
        stubExistenceCheck(0);

        StepVerifier.create(adapter.mergeTechnology("DoesNotExist", "Go"))
                .expectNext(false)
                .verifyComplete();

        verify(session, times(1)).run(anyString(), any(Value.class));
    }

    @Test
    void mergeCompany_bothNodesExist_redirectsCompanyRelTypesWithoutRelatedTo() {
        stubExistenceCheck(1);
        Result genericResult = mock(Result.class);
        lenient().when(session.run(anyString(), any(Value.class))).thenReturn(genericResult);
        when(session.run(contains("RETURN count(*)"), any(Value.class))).thenReturn(existsResult);

        StepVerifier.create(adapter.mergeCompany("dup-id", "canonical-id"))
                .expectNext(true)
                .verifyComplete();

        ArgumentCaptor<String> cypherCaptor = ArgumentCaptor.forClass(String.class);
        verify(session, atLeastOnce()).run(cypherCaptor.capture(), any(Value.class));
        List<String> cyphers = cypherCaptor.getAllValues();

        // existence check + 3 incoming (MENTIONS/POSTED_BY/HIRES_FOR) + 1 outgoing (USES)
        // + DETACH DELETE = 6. No RELATED_TO for Company.
        assertThat(cyphers).hasSize(6);
        assertThat(cyphers).noneMatch(c -> c.contains("RELATED_TO"));
        assertThat(cyphers).anyMatch(c -> c.contains("POSTED_BY"));
        assertThat(cyphers).anyMatch(c -> c.contains("HIRES_FOR"));
        assertThat(cyphers).anyMatch(c -> c.contains("USES"));
        assertThat(cyphers.get(cyphers.size() - 1)).contains("DETACH DELETE");
    }
}
