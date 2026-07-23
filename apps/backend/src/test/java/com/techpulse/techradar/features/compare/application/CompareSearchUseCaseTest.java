package com.techpulse.techradar.features.compare.application;

import com.techpulse.techradar.features.compare.domain.TechComparisonSeries;
import com.techpulse.techradar.features.radar.domain.MonthlyCount;
import com.techpulse.techradar.features.radar.ports.RadarQueryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompareSearchUseCaseTest {

    @Mock
    private RadarQueryRepository radarQueryRepository;

    private CompareSearchUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new CompareSearchUseCase(radarQueryRepository);
    }

    @Test
    void execute_returnsEmpty_withoutQuerying_whenKeywordsNull() {
        StepVerifier.create(useCase.execute(null, 12)).expectNext(List.of()).verifyComplete();

        verifyNoInteractions(radarQueryRepository);
    }

    @Test
    void execute_returnsEmpty_withoutQuerying_whenKeywordsEmpty() {
        StepVerifier.create(useCase.execute(List.of(), 12)).expectNext(List.of()).verifyComplete();

        verifyNoInteractions(radarQueryRepository);
    }

    @Test
    void execute_returnsEmpty_whenKeywordsAreAllBlank() {
        StepVerifier.create(useCase.execute(List.of("  ", ""), 12)).expectNext(List.of()).verifyComplete();

        verifyNoInteractions(radarQueryRepository);
    }

    @Test
    void execute_trimsKeywordsAndDropsBlankOnes() {
        when(radarQueryRepository.monthlySeries(eq(List.of("java", "python")), anyInt())).thenReturn(Flux.empty());

        StepVerifier.create(useCase.execute(List.of("  java  ", "", "python"), 12))
                .expectNext(List.of())
                .verifyComplete();

        verify(radarQueryRepository).monthlySeries(List.of("java", "python"), 12);
    }

    @Test
    void execute_defaultsWindowTo12Months_whenNonPositive() {
        when(radarQueryRepository.monthlySeries(any(), eq(12))).thenReturn(Flux.empty());

        useCase.execute(List.of("java"), 0).block();

        verify(radarQueryRepository).monthlySeries(List.of("java"), 12);
    }

    @Test
    void execute_clampsWindowTo60Months() {
        when(radarQueryRepository.monthlySeries(any(), eq(60))).thenReturn(Flux.empty());

        useCase.execute(List.of("java"), 999).block();

        verify(radarQueryRepository).monthlySeries(List.of("java"), 60);
        verify(radarQueryRepository, never()).monthlySeries(any(), eq(999));
    }

    @Test
    void execute_groupsRowsByTechnology_keepingLatestRatesAndFullHistory() {
        MonthlyCount javaJan = new MonthlyCount("java", 2026, 1, 10, 5, 0.1, 0.2, 0.3);
        MonthlyCount javaFeb = new MonthlyCount("java", 2026, 2, 12, 6, 0.4, 0.5, 0.6);
        MonthlyCount pythonJan = new MonthlyCount("python", 2026, 1, 20, 8, 0.7, 0.8, 0.9);
        when(radarQueryRepository.monthlySeries(any(), anyInt()))
                .thenReturn(Flux.just(javaJan, javaFeb, pythonJan));

        StepVerifier.create(useCase.execute(List.of("java", "python"), 12))
                .assertNext(result -> {
                    org.assertj.core.api.Assertions.assertThat(result).hasSize(2);
                    TechComparisonSeries java = result.get(0);
                    org.assertj.core.api.Assertions.assertThat(java.name()).isEqualTo("java");
                    org.assertj.core.api.Assertions.assertThat(java.yoyRate()).isEqualTo(0.4);
                    org.assertj.core.api.Assertions.assertThat(java.momRate()).isEqualTo(0.5);
                    org.assertj.core.api.Assertions.assertThat(java.growthRate()).isEqualTo(0.6);
                    org.assertj.core.api.Assertions.assertThat(java.monthly()).containsExactly(javaJan, javaFeb);

                    TechComparisonSeries python = result.get(1);
                    org.assertj.core.api.Assertions.assertThat(python.name()).isEqualTo("python");
                    org.assertj.core.api.Assertions.assertThat(python.monthly()).containsExactly(pythonJan);
                })
                .verifyComplete();
    }

    @Test
    void execute_propagatesErrorFromRepository() {
        RuntimeException failure = new RuntimeException("tech_analytics query failed");
        when(radarQueryRepository.monthlySeries(any(), anyInt())).thenReturn(Flux.error(failure));

        StepVerifier.create(useCase.execute(List.of("java"), 12))
                .expectErrorMatches(e -> e == failure)
                .verify();
    }
}
