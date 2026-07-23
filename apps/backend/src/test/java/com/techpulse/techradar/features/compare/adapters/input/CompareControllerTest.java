package com.techpulse.techradar.features.compare.adapters.input;

import com.techpulse.techradar.features.compare.application.CompareSearchUseCase;
import com.techpulse.techradar.features.compare.application.GenerateLlmSummaryUseCase;
import com.techpulse.techradar.features.compare.domain.TechComparison;
import com.techpulse.techradar.features.compare.domain.TechComparisonSeries;
import com.techpulse.techradar.features.radar.domain.MonthlyCount;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompareControllerTest {

    @Mock
    private CompareSearchUseCase compareSearchUseCase;
    @Mock
    private GenerateLlmSummaryUseCase generateLlmSummaryUseCase;

    private CompareController controller;

    @BeforeEach
    void setUp() {
        controller = new CompareController(compareSearchUseCase, generateLlmSummaryUseCase);
    }

    @Test
    void compare_mapsSeriesToCompareItems() {
        MonthlyCount month = new MonthlyCount("Rust", 2026, 7, 12, 3, 0.1, 0.2, 0.3);
        TechComparisonSeries series = new TechComparisonSeries("Rust", 0.1, 0.2, 0.3, List.of(month));
        when(compareSearchUseCase.execute(List.of("Rust"), 12)).thenReturn(Mono.just(List.of(series)));

        StepVerifier.create(controller.compare(List.of("Rust"), 12))
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                    List<CompareDtos.CompareItem> items = response.getBody().getData();
                    assertThat(items).hasSize(1);
                    CompareDtos.CompareItem item = items.get(0);
                    assertThat(item.getKeyword()).isEqualTo("Rust");
                    assertThat(item.getYoyRate()).isEqualTo(0.1);
                    assertThat(item.getMonthly()).hasSize(1);
                    assertThat(item.getMonthly().get(0).getMonth()).isEqualTo(7);
                    assertThat(item.getMonthly().get(0).getArticleCount()).isEqualTo(3);
                })
                .verifyComplete();
    }

    @Test
    void compare_returns400_whenUseCaseErrors() {
        when(compareSearchUseCase.execute(List.of(), 12))
                .thenReturn(Mono.error(new IllegalArgumentException("At least one keyword is required")));

        StepVerifier.create(controller.compare(List.of(), 12))
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(response.getBody().getErrorCode()).isEqualTo("COMPARISON_ERROR");
                })
                .verifyComplete();
    }

    @Test
    void generateSummary_returnsWrappedSummary() {
        TechComparison comparison = TechComparison.builder().technology1("Rust").technology2("Go").build();
        when(generateLlmSummaryUseCase.execute(comparison)).thenReturn(Mono.just("Rust is trending up."));

        StepVerifier.create(controller.generateSummary(comparison))
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                    assertThat(response.getBody().getData().getSummary()).isEqualTo("Rust is trending up.");
                })
                .verifyComplete();
    }

    @Test
    void generateSummary_returns503_whenLlmServiceUnavailable() {
        TechComparison comparison = TechComparison.builder().technology1("Rust").technology2("Go").build();
        when(generateLlmSummaryUseCase.execute(comparison))
                .thenReturn(Mono.error(new RuntimeException("connection refused")));

        StepVerifier.create(controller.generateSummary(comparison))
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(response.getBody().getErrorCode()).isEqualTo("SERVICE_UNAVAILABLE");
                })
                .verifyComplete();
    }
}
