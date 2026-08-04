package com.techpulse.techradar.features.kafka.adapters.output;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.techpulse.techradar.features.kafka.event.ExtractedJob;
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
import org.neo4j.driver.TransactionContext;
import org.neo4j.driver.TransactionCallback;
import org.neo4j.driver.Value;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers {@link Neo4jExtractionWriter#writeJob}:
 * <ul>
 *   <li>the "is new" flag is read back from the same MERGE query's RETURN clause (not from a
 *       separate preceding read), so it reflects whatever the Job MERGE Cypher / mocked driver
 *       reports for that single transaction;</li>
 *   <li>the Company MERGE keeps its CASE-based conditional SET so industry/size from an earlier
 *       job posting aren't overwritten with blanks by a crawler that doesn't scrape them.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class Neo4jExtractionWriterTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Mock
    private Driver neo4jDriver;

    @Mock
    private Session session;

    @Mock
    private TransactionContext tx;

    @Mock
    private Result jobMergeResult;

    @Mock
    private Record jobMergeRecord;

    private Neo4jExtractionWriter writer;

    @BeforeEach
    void setUp() {
        writer = new Neo4jExtractionWriter(neo4jDriver);
        when(neo4jDriver.session()).thenReturn(session);
        when(session.executeWrite(any())).thenAnswer(invocation -> {
            TransactionCallback<Object> work = invocation.getArgument(0);
            return work.execute(tx);
        });
        // The Job MERGE is the only call whose return value writeJob() reads (.single()), so a
        // blanket stub for tx.run(...) is safe: the Company/Technology/Skill MERGE calls never
        // touch the Result they get back.
        when(tx.run(contains("MERGE (j:Job"), any(Value.class))).thenReturn(jobMergeResult);
        when(jobMergeResult.single()).thenReturn(jobMergeRecord);
    }

    private ExtractedJob extractedJob(String companySizeField, String companyIndustryField) throws Exception {
        return extractedJob("\"Acme Corp\"", companySizeField, companyIndustryField);
    }

    private ExtractedJob extractedJob(String companyNameField, String companySizeField, String companyIndustryField)
            throws Exception {
        String json = ("""
                {
                  "message_type": "extracted_job",
                  "source_platform": "TopCV",
                  "crawled_at": "2026-01-01T00:00:00Z",
                  "extracted_at": "2026-01-01T00:00:00Z",
                  "data": {
                    "job": {
                      "title": "Backend Developer",
                      "description": "desc",
                      "requirement": "req",
                      "benefit": "benefit",
                      "salary": "20-30 triệu",
                      "due_date": "",
                      "source_url": "https://example.com/job-1"
                    },
                    "company": {
                      "name": %s,
                      "size": %s,
                      "field": %s,
                      "location": "Hà Nội"
                    },
                    "skills": [],
                    "technologies": []
                  }
                }
                """).formatted(companyNameField, companySizeField, companyIndustryField);
        return objectMapper.readValue(json, ExtractedJob.class);
    }

    private void stubIsNew(boolean isNew) {
        Value isNewValue = mock(Value.class);
        when(isNewValue.asBoolean()).thenReturn(isNew);
        when(jobMergeRecord.get("isNew")).thenReturn(isNewValue);
    }

    private String companyMergeCypher() {
        ArgumentCaptor<String> cypherCaptor = ArgumentCaptor.forClass(String.class);
        verify(tx, atLeastOnce()).run(cypherCaptor.capture(), any(Value.class));
        return cypherCaptor.getAllValues().stream()
                .filter(cypher -> cypher.contains("MERGE (c:Company"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No Company MERGE Cypher was run"));
    }

    private Value companyMergeParams() {
        ArgumentCaptor<String> cypherCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Value> paramsCaptor = ArgumentCaptor.forClass(Value.class);
        verify(tx, atLeastOnce()).run(cypherCaptor.capture(), paramsCaptor.capture());
        List<String> cyphers = cypherCaptor.getAllValues();
        for (int i = 0; i < cyphers.size(); i++) {
            if (cyphers.get(i).contains("MERGE (c:Company")) {
                return paramsCaptor.getAllValues().get(i);
            }
        }
        throw new AssertionError("No Company MERGE Cypher was run");
    }

    @Test
    void writeJob_returnsTrueWhenTheJobMergeQueryReportsItCreatedTheNode() throws Exception {
        stubIsNew(true);

        boolean isNew = writer.writeJob(extractedJob("\"100-500\"", "\"Fintech\""));

        assertThat(isNew).isTrue();
    }

    @Test
    void writeJob_returnsFalseWhenTheJobMergeQueryReportsTheNodeAlreadyExisted() throws Exception {
        stubIsNew(false);

        boolean isNew = writer.writeJob(extractedJob("\"100-500\"", "\"Fintech\""));

        assertThat(isNew).isFalse();
    }

    @Test
    void writeJob_computesIsNewInsideTheSameJobMergeStatementRatherThanASeparatePrecedingRead() throws Exception {
        stubIsNew(true);

        writer.writeJob(extractedJob("\"100-500\"", "\"Fintech\""));

        // The isNew determination must come back from the Job MERGE's own RETURN clause, and
        // that MERGE must run inside the same session.executeWrite(...) transaction as the rest
        // of the write — never via a separate tx-less session.run(...) issued beforehand.
        ArgumentCaptor<String> cypherCaptor = ArgumentCaptor.forClass(String.class);
        verify(tx, atLeastOnce()).run(cypherCaptor.capture(), any(Value.class));
        String jobMergeCypher = cypherCaptor.getAllValues().stream()
                .filter(c -> c.contains("MERGE (j:Job"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No Job MERGE Cypher was run"));
        assertThat(jobMergeCypher).contains("RETURN isNew");
        assertThat(jobMergeCypher).contains("ON CREATE SET");
    }

    @Test
    void writeJob_usesConditionalCaseInsteadOfUnconditionallyOverwritingIndustryAndSize() throws Exception {
        stubIsNew(false);

        writer.writeJob(extractedJob("\"100-500\"", "\"Fintech\""));

        String cypher = companyMergeCypher();
        assertThat(cypher).contains("CASE WHEN $company_industry IS NULL OR $company_industry = ''");
        assertThat(cypher).contains("CASE WHEN $company_size IS NULL OR $company_size = ''");
    }

    @Test
    void writeJob_passesTheIncomingIndustryAndSizeAsCypherParameters() throws Exception {
        stubIsNew(false);

        writer.writeJob(extractedJob("\"100-500\"", "\"Fintech\""));

        Value params = companyMergeParams();
        assertThat(params.get("company_size").asString()).isEqualTo("100-500");
        assertThat(params.get("company_industry").asString()).isEqualTo("Fintech");
    }

    @Test
    void writeJob_passesBlankIndustryAndSizeWhenTheJobPostingHasNone() throws Exception {
        stubIsNew(false);

        writer.writeJob(extractedJob("\"\"", "\"\""));

        Value params = companyMergeParams();
        assertThat(params.get("company_size").asString()).isEmpty();
        assertThat(params.get("company_industry").asString()).isEmpty();
    }

    @Test
    void writeJob_skipsCompanyMergeWhenCompanyNameIsBlank() throws Exception {
        stubIsNew(false);

        // An empty (non-null) company name previously slipped past the old `getName() != null`
        // guard and slugified to an empty string, creating a real Company{id:"", name:""}
        // garbage node with zero relationships (confirmed live on 2026-07-24). Must be skipped
        // entirely instead of MERGEd.
        writer.writeJob(extractedJob("\"\"", "\"100-500\"", "\"Fintech\""));

        verify(tx, never()).run(contains("MERGE (c:Company"), any(Value.class));
    }

    @Test
    void writeJob_skipsCompanyMergeWhenCompanyNameIsOnlyPunctuation() throws Exception {
        stubIsNew(false);

        // Not blank per String.isBlank(), but slugify() strips every non a-z0-9 character —
        // "---" reduces to an empty id, the same garbage-node class as a truly blank name.
        writer.writeJob(extractedJob("\"---\"", "\"100-500\"", "\"Fintech\""));

        verify(tx, never()).run(contains("MERGE (c:Company"), any(Value.class));
    }

    @Test
    void writeJob_setsLevelOnTheJobNodeAndPassesItAsACypherParameter() throws Exception {
        stubIsNew(false);
        String json = """
                {
                  "message_type": "extracted_job",
                  "source_platform": "TopCV",
                  "crawled_at": "2026-01-01T00:00:00Z",
                  "extracted_at": "2026-01-01T00:00:00Z",
                  "data": {
                    "job": {
                      "title": "Backend Developer",
                      "description": "desc",
                      "requirement": "req",
                      "benefit": "benefit",
                      "salary": "20-30 triệu",
                      "level": "Senior",
                      "due_date": "",
                      "source_url": "https://example.com/job-1"
                    },
                    "company": null,
                    "skills": [],
                    "technologies": []
                  }
                }
                """;
        ExtractedJob extracted = objectMapper.readValue(json, ExtractedJob.class);

        writer.writeJob(extracted);

        ArgumentCaptor<String> cypherCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Value> paramsCaptor = ArgumentCaptor.forClass(Value.class);
        verify(tx, atLeastOnce()).run(cypherCaptor.capture(), paramsCaptor.capture());
        int jobMergeIndex = cypherCaptor.getAllValues().indexOf(
                cypherCaptor.getAllValues().stream()
                        .filter(c -> c.contains("MERGE (j:Job"))
                        .findFirst()
                        .orElseThrow(() -> new AssertionError("No Job MERGE Cypher was run")));

        assertThat(cypherCaptor.getAllValues().get(jobMergeIndex)).contains("j.level = $level");
        assertThat(paramsCaptor.getAllValues().get(jobMergeIndex).get("level").asString()).isEqualTo("Senior");
    }
}
