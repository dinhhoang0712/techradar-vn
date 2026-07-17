package com.techpulse.techradar.features.salary.application;

import com.techpulse.techradar.features.salary.domain.SalaryInsight;
import com.techpulse.techradar.features.salary.ports.SalaryRepository;
import com.techpulse.techradar.shared.redis.ReactiveRedisCache;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetSalaryInsightsUseCaseTest {

    @Mock
    private SalaryRepository salaryRepository;

    @Mock
    private ReactiveRedisCache redisCache;

    private GetSalaryInsightsUseCase useCase;

    private void stubCacheAsPassThrough() {
        when(redisCache.getOrLoad(anyString(), any(Duration.class), any(Flux.class), any()))
                .thenAnswer(invocation -> invocation.getArgument(2));
        useCase = new GetSalaryInsightsUseCase(salaryRepository, redisCache);
    }

    private static SalaryRepository.TechSalaryRaw raw(String tech, int totalJobs, List<String> salaries) {
        return new SalaryRepository.TechSalaryRaw(tech, totalJobs, salaries);
    }

    @Test
    void execute_buildsCacheKeyFromEffectiveLimitAndMinJobs() {
        stubCacheAsPassThrough();
        when(salaryRepository.findTechSalaries(anyInt(), anyInt())).thenReturn(Flux.empty());

        useCase.execute(40, 1).blockLast();

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(redisCache).getOrLoad(keyCaptor.capture(), any(Duration.class), any(Flux.class), any());
        assertThat(keyCaptor.getValue()).isEqualTo("cache:salary:top:40:1");
    }

    @Test
    void execute_defaultsMinJobsToOneWhenNonPositive() {
        stubCacheAsPassThrough();
        when(salaryRepository.findTechSalaries(anyInt(), anyInt())).thenReturn(Flux.empty());

        useCase.execute(40, 0).blockLast();

        verify(salaryRepository).findTechSalaries(eq(1), anyInt());
    }

    @Test
    void execute_defaultsLimitTo20WhenNonPositive() {
        stubCacheAsPassThrough();
        when(salaryRepository.findTechSalaries(anyInt(), anyInt())).thenReturn(Flux.empty());

        useCase.execute(0, 1).blockLast();

        // techLimit passed to the repository is effectiveLimit * 3.
        verify(salaryRepository).findTechSalaries(anyInt(), eq(60));
    }

    @Test
    void execute_clampsLimitToMax100() {
        stubCacheAsPassThrough();
        when(salaryRepository.findTechSalaries(anyInt(), anyInt())).thenReturn(Flux.empty());

        useCase.execute(500, 1).blockLast();

        verify(salaryRepository).findTechSalaries(anyInt(), eq(300));
    }

    @Test
    void execute_filtersOutTechsWhereNoSalaryStringParses() {
        stubCacheAsPassThrough();
        when(salaryRepository.findTechSalaries(anyInt(), anyInt())).thenReturn(Flux.just(
                raw("COBOL", 3, List.of("Thỏa thuận", "Negotiable"))));

        StepVerifier.create(useCase.execute(40, 1)).verifyComplete();
    }

    @Test
    void execute_computesStatsFromParseableSalariesAndIgnoresUnparseableOnes() {
        stubCacheAsPassThrough();
        when(salaryRepository.findTechSalaries(anyInt(), anyInt())).thenReturn(Flux.just(
                raw("Java", 3, List.of("10 - 20 triệu", "15 - 25 triệu", "Thỏa thuận"))));

        StepVerifier.create(useCase.execute(40, 1))
                .assertNext(insight -> {
                    assertThat(insight.techName()).isEqualTo("Java");
                    assertThat(insight.totalJobs()).isEqualTo(3);
                    assertThat(insight.jobsWithSalary()).isEqualTo(2);
                    assertThat(insight.medianVnd()).isEqualTo(17.5);
                    assertThat(insight.avgVnd()).isEqualTo(17.5);
                    assertThat(insight.minVnd()).isEqualTo(15.0);
                    assertThat(insight.maxVnd()).isEqualTo(20.0);
                    assertThat(insight.p25Vnd()).isEqualTo(16.3);
                    assertThat(insight.p75Vnd()).isEqualTo(18.8);
                })
                .verifyComplete();
    }

    @Test
    void execute_sortsResultsByMedianDescending() {
        stubCacheAsPassThrough();
        when(salaryRepository.findTechSalaries(anyInt(), anyInt())).thenReturn(Flux.just(
                raw("Java", 1, List.of("10 - 20 triệu")),
                raw("Python", 1, List.of("40 - 60 triệu"))));

        StepVerifier.create(useCase.execute(40, 1).map(SalaryInsight::techName))
                .expectNext("Python", "Java")
                .verifyComplete();
    }
}
