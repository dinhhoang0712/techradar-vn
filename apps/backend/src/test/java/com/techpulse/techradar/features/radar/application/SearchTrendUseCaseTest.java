package com.techpulse.techradar.features.radar.application;

import com.techpulse.techradar.features.radar.domain.MonthlyCount;
import com.techpulse.techradar.features.radar.ports.RadarQueryRepository;
import com.techpulse.techradar.shared.redis.ReactiveRedisCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SearchTrendUseCaseTest {

    @Mock
    private RadarQueryRepository radarQueryRepository;
    @Mock
    private ReactiveRedisCache redisCache;

    private SearchTrendUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new SearchTrendUseCase(radarQueryRepository, redisCache);
        ReflectionTestUtils.setField(useCase, "cacheTtlSeconds", 3600L);
        lenient().when(redisCache.getOrLoad(anyString(), any(Duration.class), any(Flux.class), any()))
                .thenAnswer(invocation -> invocation.getArgument(2));
    }

    @Test
    void execute_returnsEmpty_withoutQuerying_whenKeywordsNull() {
        StepVerifier.create(useCase.execute(null, 6)).verifyComplete();

        verify(radarQueryRepository, never()).monthlySeries(any(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void execute_returnsEmpty_withoutQuerying_whenKeywordsEmpty() {
        StepVerifier.create(useCase.execute(List.of(), 6)).verifyComplete();

        verify(radarQueryRepository, never()).monthlySeries(any(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void execute_returnsEmpty_withoutQuerying_whenAllKeywordsBlank() {
        StepVerifier.create(useCase.execute(List.of("  ", ""), 6)).verifyComplete();

        verify(radarQueryRepository, never()).monthlySeries(any(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void execute_trimsSortsAndDropsBlankKeywordsBeforeQuerying() {
        when(radarQueryRepository.monthlySeries(List.of("go", "java"), 6)).thenReturn(Flux.empty());

        StepVerifier.create(useCase.execute(List.of("  java", "go ", ""), 6)).verifyComplete();

        verify(radarQueryRepository).monthlySeries(List.of("go", "java"), 6);
    }

    @Test
    void execute_defaultsWindowToSixMonths_whenNonPositive() {
        when(radarQueryRepository.monthlySeries(any(), org.mockito.ArgumentMatchers.eq(6))).thenReturn(Flux.empty());

        StepVerifier.create(useCase.execute(List.of("java"), 0)).verifyComplete();

        verify(radarQueryRepository).monthlySeries(List.of("java"), 6);
    }

    @Test
    void execute_clampsWindowToSixtyMonths() {
        when(radarQueryRepository.monthlySeries(any(), org.mockito.ArgumentMatchers.eq(60))).thenReturn(Flux.empty());

        StepVerifier.create(useCase.execute(List.of("java"), 999)).verifyComplete();

        verify(radarQueryRepository).monthlySeries(List.of("java"), 60);
    }

    @Test
    void execute_returnsMonthlyCountsFromRepository() {
        MonthlyCount count = new MonthlyCount("Java", 2026, 7, 100, 20, 5.0, 2.0, 3.0);
        when(radarQueryRepository.monthlySeries(List.of("java"), 6)).thenReturn(Flux.just(count));

        StepVerifier.create(useCase.execute(List.of("java"), 6))
                .expectNext(count)
                .verifyComplete();
    }

    @Test
    void execute_usesCacheKeyDerivedFromSortedKeywordsAndWindow() {
        when(radarQueryRepository.monthlySeries(any(), org.mockito.ArgumentMatchers.anyInt())).thenReturn(Flux.empty());

        useCase.execute(List.of("java", "go"), 12).blockLast();

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(redisCache).getOrLoad(keyCaptor.capture(), any(Duration.class), any(Flux.class), any());
        assertThat(keyCaptor.getValue()).isEqualTo(SearchTrendUseCase.CACHE_KEY_PREFIX + "go,java:12");
    }
}
