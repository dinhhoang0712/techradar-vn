package com.techpulse.techradar.features.salary.adapters.output;

import com.techpulse.techradar.features.salary.ports.SalaryRepository.TechSalaryDetailRaw;
import com.techpulse.techradar.features.salary.ports.SalaryRepository.TechSalaryRaw;
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
import java.util.Map;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the Cypher text, bound parameters, and row-mapping for {@link Neo4jSalaryRepository}, in
 * particular {@link Neo4jSalaryRepository#findTechSalaryDetail}'s short-circuit (no co-tech query
 * fired at all when the tech has zero salary-bearing jobs) and its multi-query composition.
 */
@ExtendWith(MockitoExtension.class)
class Neo4jSalaryRepositoryTest {

    @Mock
    private Driver driver;
    @Mock
    private Session session;
    @Mock
    private Result result;
    @Mock
    private Record record;

    private Neo4jSalaryRepository repository;

    @BeforeEach
    void setUp() {
        repository = new Neo4jSalaryRepository(driver);
        lenient().when(driver.session()).thenReturn(session);
    }

    private static Value stringValue(String s) {
        Value v = mock(Value.class);
        lenient().when(v.asString()).thenReturn(s);
        return v;
    }

    @Test
    @SuppressWarnings("unchecked")
    void findTechSalaries_bindsMinJobsAndLimit_andMapsAllThreeColumns() {
        when(session.run(anyString(), any(Map.class))).thenReturn(result);
        when(result.list()).thenReturn(List.of(record));

        Value techNameValue = stringValue("Java");
        Value totalJobsValue = mock(Value.class);
        when(totalJobsValue.asInt()).thenReturn(25);
        Value salariesValue = mock(Value.class);
        when(salariesValue.asList(any(Function.class))).thenReturn(List.of("20-30tr", "30-40tr"));

        when(record.get("techName")).thenReturn(techNameValue);
        when(record.get("totalJobs")).thenReturn(totalJobsValue);
        when(record.get("salaries")).thenReturn(salariesValue);

        StepVerifier.create(repository.findTechSalaries(5, 20))
                .assertNext((TechSalaryRaw raw) -> {
                    assertThat(raw.techName()).isEqualTo("Java");
                    assertThat(raw.totalJobs()).isEqualTo(25);
                    assertThat(raw.salaries()).containsExactly("20-30tr", "30-40tr");
                })
                .verifyComplete();

        ArgumentCaptor<Map<String, Object>> paramsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(session).run(anyString(), paramsCaptor.capture());
        assertThat(paramsCaptor.getValue()).containsEntry("minJobs", 5).containsEntry("limit", 20);
    }

    @Test
    @SuppressWarnings("unchecked")
    void findTechSalaries_returnsEmpty_whenNoTechMeetsTheMinJobsThreshold() {
        when(session.run(anyString(), any(Map.class))).thenReturn(result);
        when(result.list()).thenReturn(Collections.emptyList());

        StepVerifier.create(repository.findTechSalaries(100, 20)).verifyComplete();
    }

    @Test
    @SuppressWarnings("unchecked")
    void findTechSalaryDetail_returnsZeroedDefault_andNeverRunsTheCoTechQuery_whenNoSalaryRecordFound() {
        when(session.run(argThat((String q) -> q != null && q.contains("RETURN count(j) AS totalJobs")), any(Map.class)))
                .thenReturn(result);
        when(result.list()).thenReturn(Collections.emptyList());

        StepVerifier.create(repository.findTechSalaryDetail("Cobol"))
                .assertNext(detail -> {
                    assertThat(detail.techName()).isEqualTo("Cobol");
                    assertThat(detail.totalJobs()).isZero();
                    assertThat(detail.salaries()).isEmpty();
                    assertThat(detail.coTechs()).isEmpty();
                })
                .verifyComplete();

        verify(session, never()).run(argThat((String q) -> q != null && q.contains("co.name AS coTech")), any(Map.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void findTechSalaryDetail_mapsTotalJobsSalariesAndCoTechs_whenSalaryDataExists() {
        Result salaryResult = mock(Result.class);
        Record salaryRecord = mock(Record.class);
        Value totalJobsValue = mock(Value.class);
        when(totalJobsValue.asInt()).thenReturn(10);
        Value salariesValue = mock(Value.class);
        when(salariesValue.asList(any(Function.class))).thenReturn(List.of("15-20tr"));
        when(salaryRecord.get("totalJobs")).thenReturn(totalJobsValue);
        when(salaryRecord.get("salaries")).thenReturn(salariesValue);
        when(salaryResult.list()).thenReturn(List.of(salaryRecord));

        Result coResult = mock(Result.class);
        Record coRecord1 = mock(Record.class);
        Value coTech1 = stringValue("Spring");
        Value cnt1 = mock(Value.class);
        when(cnt1.asInt()).thenReturn(8);
        when(coRecord1.get("coTech")).thenReturn(coTech1);
        when(coRecord1.get("cnt")).thenReturn(cnt1);
        Record coRecord2 = mock(Record.class);
        Value coTech2 = stringValue("Hibernate");
        Value cnt2 = mock(Value.class);
        when(cnt2.asInt()).thenReturn(4);
        when(coRecord2.get("coTech")).thenReturn(coTech2);
        when(coRecord2.get("cnt")).thenReturn(cnt2);
        when(coResult.list()).thenReturn(List.of(coRecord1, coRecord2));

        when(session.run(argThat((String q) -> q != null && q.contains("RETURN count(j) AS totalJobs")), any(Map.class)))
                .thenReturn(salaryResult);
        when(session.run(argThat((String q) -> q != null && q.contains("co.name AS coTech")), any(Map.class)))
                .thenReturn(coResult);

        StepVerifier.create(repository.findTechSalaryDetail("Java"))
                .assertNext((TechSalaryDetailRaw detail) -> {
                    assertThat(detail.techName()).isEqualTo("Java");
                    assertThat(detail.totalJobs()).isEqualTo(10);
                    assertThat(detail.salaries()).containsExactly("15-20tr");
                    assertThat(detail.coTechs()).extracting(Map.Entry::getKey)
                            .containsExactly("Spring", "Hibernate");
                    assertThat(detail.coTechs()).extracting(Map.Entry::getValue)
                            .containsExactly(8, 4);
                })
                .verifyComplete();
    }
}
