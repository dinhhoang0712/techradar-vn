package com.techpulse.techradar.features.job.adapters.output;

import com.techpulse.techradar.features.job.ports.JobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.Value;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class Neo4jJobRepositoryTest {

    @Mock
    private Driver driver;
    @Mock
    private Session session;
    @Mock
    private Result result;
    @Mock
    private Record record;

    private Neo4jJobRepository repository;

    @BeforeEach
    void setUp() {
        repository = new Neo4jJobRepository(driver);
        lenient().when(driver.session()).thenReturn(session);
    }

    private static Value nullValue() {
        Value v = org.mockito.Mockito.mock(Value.class);
        when(v.isNull()).thenReturn(true);
        return v;
    }

    private static Value stringValue(String s) {
        Value v = org.mockito.Mockito.mock(Value.class);
        lenient().when(v.isNull()).thenReturn(false);
        lenient().when(v.asString()).thenReturn(s);
        return v;
    }

    @Test
    void findMatchingJobs_returnsEmpty_whenNoUserSkills_withoutTouchingTheDriver() {
        StepVerifier.create(repository.findMatchingJobs(List.of(), 20)).verifyComplete();
        StepVerifier.create(repository.findMatchingJobs(null, 20)).verifyComplete();

        verify(driver, never()).session();
    }

    @Test
    void findMatchingJobs_mapsRecordFieldsAndTreatsNullColumnsAsNull() {
        when(session.run(anyString(), any(Map.class))).thenReturn(result);
        when(result.list()).thenReturn(List.of(record));

        Value titleValue = stringValue("Backend Dev");
        Value companyValue = stringValue("Acme");
        Value locationValue = stringValue("Hà Nội");
        Value salaryValue = nullValue();
        Value levelValue = stringValue("Senior");
        Value sourceUrlValue = nullValue();
        Value dueDateValue = nullValue();
        Value requiredValue = org.mockito.Mockito.mock(Value.class);
        when(requiredValue.asList(org.mockito.ArgumentMatchers.<Function<Value, String>>any()))
                .thenReturn(List.of("Java", "Spring"));
        Value matchedValue = org.mockito.Mockito.mock(Value.class);
        when(matchedValue.asList(org.mockito.ArgumentMatchers.<Function<Value, String>>any()))
                .thenReturn(List.of("Java"));
        Value scoreValue = org.mockito.Mockito.mock(Value.class);
        when(scoreValue.asDouble()).thenReturn(0.5);

        when(record.get("title")).thenReturn(titleValue);
        when(record.get("company")).thenReturn(companyValue);
        when(record.get("location")).thenReturn(locationValue);
        when(record.get("salary")).thenReturn(salaryValue);
        when(record.get("level")).thenReturn(levelValue);
        when(record.get("sourceUrl")).thenReturn(sourceUrlValue);
        when(record.get("dueDate")).thenReturn(dueDateValue);
        when(record.get("required")).thenReturn(requiredValue);
        when(record.get("matched")).thenReturn(matchedValue);
        when(record.get("score")).thenReturn(scoreValue);

        StepVerifier.create(repository.findMatchingJobs(List.of("java"), 20))
                .assertNext(raw -> {
                    assertThat(raw.title()).isEqualTo("Backend Dev");
                    assertThat(raw.company()).isEqualTo("Acme");
                    assertThat(raw.salary()).isNull();
                    assertThat(raw.level()).isEqualTo("Senior");
                    assertThat(raw.sourceUrl()).isNull();
                    assertThat(raw.dueDate()).isNull();
                    assertThat(raw.required()).containsExactly("Java", "Spring");
                    assertThat(raw.matched()).containsExactly("Java");
                    assertThat(raw.score()).isEqualTo(0.5);
                })
                .verifyComplete();
    }

    @Test
    void countJobs_returnsCountFromSingleRecord() {
        Value countValue = org.mockito.Mockito.mock(Value.class);
        when(countValue.asLong()).thenReturn(42L);
        when(record.get("c")).thenReturn(countValue);
        when(result.single()).thenReturn(record);
        when(session.run("MATCH (j:Job) RETURN count(j) AS c")).thenReturn(result);

        StepVerifier.create(repository.countJobs()).expectNext(42L).verifyComplete();
    }

    @Test
    void topTechnologies_mapsNameAndJobCount() {
        when(session.run(anyString(), any(Map.class))).thenReturn(result);
        when(result.list()).thenReturn(List.of(record));
        Value nameValue = stringValue("Kotlin");
        Value jobCountValue = org.mockito.Mockito.mock(Value.class);
        when(jobCountValue.asLong()).thenReturn(7L);
        when(record.get("name")).thenReturn(nameValue);
        when(record.get("jobCount")).thenReturn(jobCountValue);

        StepVerifier.create(repository.topTechnologies(10))
                .assertNext(demand -> {
                    assertThat(demand.name()).isEqualTo("Kotlin");
                    assertThat(demand.jobCount()).isEqualTo(7L);
                })
                .verifyComplete();
    }
}
