package com.techpulse.techradar.features.radar.application;

import com.techpulse.techradar.features.radar.domain.TechSnapshot;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetTopTechnologiesUseCaseTest {

    @Mock
    private RadarQueryRepository radarQueryRepository;
    @Mock
    private ReactiveRedisCache redisCache;

    private GetTopTechnologiesUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetTopTechnologiesUseCase(radarQueryRepository, redisCache);
        ReflectionTestUtils.setField(useCase, "cacheTtlSeconds", 3600L);
        lenient().when(redisCache.getOrLoad(anyString(), any(Duration.class), any(Flux.class), any()))
                .thenAnswer(invocation -> invocation.getArgument(2));
    }

    @Test
    void execute_defaultsLimitToTen_whenNonPositive() {
        when(radarQueryRepository.topTechnologies(10)).thenReturn(Flux.empty());

        StepVerifier.create(useCase.execute(0)).verifyComplete();

        verify(radarQueryRepository).topTechnologies(10);
    }

    @Test
    void execute_clampsLimitToMaxOfOneHundred() {
        when(radarQueryRepository.topTechnologies(100)).thenReturn(Flux.empty());

        StepVerifier.create(useCase.execute(999)).verifyComplete();

        verify(radarQueryRepository).topTechnologies(100);
    }

    @Test
    void execute_returnsTopTechnologiesFromRepository() {
        TechSnapshot snapshot = new TechSnapshot("Java", 200, 15.0, 5.0, 40);
        when(radarQueryRepository.topTechnologies(4)).thenReturn(Flux.just(snapshot));

        StepVerifier.create(useCase.execute(4))
                .expectNext(snapshot)
                .verifyComplete();
    }

    @Test
    void execute_usesCacheKeyDerivedFromEffectiveLimit() {
        when(radarQueryRepository.topTechnologies(10)).thenReturn(Flux.empty());

        useCase.execute(0).blockLast();

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(redisCache).getOrLoad(keyCaptor.capture(), any(Duration.class), any(Flux.class), any());
        assertThat(keyCaptor.getValue()).isEqualTo(GetTopTechnologiesUseCase.CACHE_KEY_PREFIX + "10");
    }
}
