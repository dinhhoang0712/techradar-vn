package com.techpulse.techradar.features.company.adapters.output;

import com.techpulse.techradar.features.company.domain.CompanyMention;
import com.techpulse.techradar.features.company.ports.CompanyRepository.CompanyRaw;
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

import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the Cypher text and row-mapping for {@link Neo4jCompanyRepository}: the tech-stack
 * inference query (which deliberately matches both the live {@code POSTED_BY} and the frozen,
 * historical {@code HIRES_FOR} edge — see the comment on {@code QUERY}) and the mentions query.
 */
@ExtendWith(MockitoExtension.class)
class Neo4jCompanyRepositoryTest {

    private static final String QUERY =
            "MATCH (c:Company)<-[:POSTED_BY|HIRES_FOR]-(j:Job)-[:REQUIRES]->(t) " +
                    "WHERE t:Technology OR t:Skill " +
                    "WITH c, collect(DISTINCT t.name) AS techStack, count(DISTINCT j) AS jobCount " +
                    "RETURN c.id AS id, c.name AS name, c.location AS location, techStack, jobCount, " +
                    "c.industry AS industry, c.size AS size " +
                    "ORDER BY jobCount DESC " +
                    "LIMIT 500";

    private static final String MENTIONS_QUERY =
            "MATCH (a:Article)-[:MENTIONS]->(c:Company {id: $company_id}) " +
                    "RETURN a.id AS id, a.title AS title, a.source_url AS url, " +
                    "a.publish_date AS publishDate, a.source_platform AS sourcePlatform " +
                    "ORDER BY a.publish_date DESC " +
                    "LIMIT $limit";

    @Mock
    private Driver driver;
    @Mock
    private Session session;
    @Mock
    private Result result;
    @Mock
    private Record record;

    private Neo4jCompanyRepository repository;

    @BeforeEach
    void setUp() {
        repository = new Neo4jCompanyRepository(driver);
        lenient().when(driver.session()).thenReturn(session);
    }

    private static Value nullValue() {
        Value v = mock(Value.class);
        when(v.isNull()).thenReturn(true);
        return v;
    }

    private static Value stringValue(String s) {
        Value v = mock(Value.class);
        lenient().when(v.isNull()).thenReturn(false);
        lenient().when(v.asString()).thenReturn(s);
        return v;
    }

    @Test
    void findAllWithTechStack_runsTheExactQuery_andReturnsEmpty_whenNoRecords() {
        when(session.run(QUERY)).thenReturn(result);
        when(result.list()).thenReturn(Collections.emptyList());

        StepVerifier.create(repository.findAllWithTechStack()).verifyComplete();

        verify(session).run(QUERY);
    }

    @Test
    @SuppressWarnings("unchecked")
    void findAllWithTechStack_mapsAllColumns_includingNullableIndustryAndSize() {
        when(session.run(QUERY)).thenReturn(result);
        when(result.list()).thenReturn(List.of(record));

        Value idValue = stringValue("c1");
        Value nameValue = stringValue("Acme Corp");
        Value locationValue = stringValue("Hanoi");
        Value industryValue = nullValue();
        Value sizeValue = nullValue();
        Value techStackValue = mock(Value.class);
        when(techStackValue.asList(any(Function.class))).thenReturn(List.of("Java", "Kotlin"));
        Value jobCountValue = mock(Value.class);
        when(jobCountValue.asInt()).thenReturn(12);

        when(record.get("id")).thenReturn(idValue);
        when(record.get("name")).thenReturn(nameValue);
        when(record.get("location")).thenReturn(locationValue);
        when(record.get("techStack")).thenReturn(techStackValue);
        when(record.get("jobCount")).thenReturn(jobCountValue);
        when(record.get("industry")).thenReturn(industryValue);
        when(record.get("size")).thenReturn(sizeValue);

        StepVerifier.create(repository.findAllWithTechStack())
                .assertNext(raw -> {
                    assertThat(raw.id()).isEqualTo("c1");
                    assertThat(raw.name()).isEqualTo("Acme Corp");
                    assertThat(raw.location()).isEqualTo("Hanoi");
                    assertThat(raw.techStack()).containsExactly("Java", "Kotlin");
                    assertThat(raw.jobCount()).isEqualTo(12);
                    assertThat(raw.industry()).isNull();
                    assertThat(raw.size()).isNull();
                })
                .verifyComplete();
    }

    @Test
    @SuppressWarnings("unchecked")
    void findAllWithTechStack_mapsNonNullIndustryAndSize() {
        when(session.run(QUERY)).thenReturn(result);
        when(result.list()).thenReturn(List.of(record));

        Value idValue = stringValue("c2");
        Value nameValue = stringValue("Beta Ltd");
        Value locationValue = stringValue("Saigon");
        Value industryValue = stringValue("Fintech");
        Value sizeValue = stringValue("51-200");
        Value techStackValue = mock(Value.class);
        when(techStackValue.asList(any(Function.class))).thenReturn(List.of("Go"));
        Value jobCountValue = mock(Value.class);
        when(jobCountValue.asInt()).thenReturn(3);

        when(record.get("id")).thenReturn(idValue);
        when(record.get("name")).thenReturn(nameValue);
        when(record.get("location")).thenReturn(locationValue);
        when(record.get("techStack")).thenReturn(techStackValue);
        when(record.get("jobCount")).thenReturn(jobCountValue);
        when(record.get("industry")).thenReturn(industryValue);
        when(record.get("size")).thenReturn(sizeValue);

        StepVerifier.create(repository.findAllWithTechStack())
                .assertNext((CompanyRaw raw) -> {
                    assertThat(raw.industry()).isEqualTo("Fintech");
                    assertThat(raw.size()).isEqualTo("51-200");
                })
                .verifyComplete();
    }

    @Test
    @SuppressWarnings("unchecked")
    void findMentions_passesCompanyIdAndLimitAsParameters() {
        when(session.run(anyString(), any(Value.class))).thenReturn(result);
        when(result.list()).thenReturn(Collections.emptyList());

        StepVerifier.create(repository.findMentions("company-42", 5)).verifyComplete();

        ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Value> paramsCaptor = ArgumentCaptor.forClass(Value.class);
        verify(session).run(queryCaptor.capture(), paramsCaptor.capture());

        assertThat(queryCaptor.getValue()).isEqualTo(MENTIONS_QUERY);
        assertThat(paramsCaptor.getValue().get("company_id").asString()).isEqualTo("company-42");
        assertThat(paramsCaptor.getValue().get("limit").asInt()).isEqualTo(5);
    }

    @Test
    @SuppressWarnings("unchecked")
    void findMentions_mapsAllFields_andTreatsNullColumnsAsNull() {
        when(session.run(anyString(), any(Value.class))).thenReturn(result);
        when(result.list()).thenReturn(List.of(record));

        Value idValue = stringValue("a1");
        Value titleValue = stringValue("Big Tech News");
        Value urlValue = nullValue();
        Value publishDateValue = stringValue("2026-07-01");
        Value sourcePlatformValue = nullValue();

        when(record.get("id")).thenReturn(idValue);
        when(record.get("title")).thenReturn(titleValue);
        when(record.get("url")).thenReturn(urlValue);
        when(record.get("publishDate")).thenReturn(publishDateValue);
        when(record.get("sourcePlatform")).thenReturn(sourcePlatformValue);

        StepVerifier.create(repository.findMentions("company-42", 5))
                .assertNext((CompanyMention m) -> {
                    assertThat(m.id()).isEqualTo("a1");
                    assertThat(m.title()).isEqualTo("Big Tech News");
                    assertThat(m.url()).isNull();
                    assertThat(m.publishDate()).isEqualTo("2026-07-01");
                    assertThat(m.sourcePlatform()).isNull();
                })
                .verifyComplete();
    }
}
