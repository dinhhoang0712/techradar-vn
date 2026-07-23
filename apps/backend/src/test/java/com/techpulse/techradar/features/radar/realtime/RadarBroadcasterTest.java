package com.techpulse.techradar.features.radar.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techpulse.techradar.features.radar.application.GetTopTechnologiesUseCase;
import com.techpulse.techradar.features.radar.domain.TechSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RadarBroadcasterTest {

    private static final String CHANNEL = "live:radar";

    @Mock
    private ReactiveRedisMessageListenerContainer redisListenerContainer;
    @Mock
    private ReactiveStringRedisTemplate redisTemplate;
    @Mock
    private GetTopTechnologiesUseCase getTopTechnologiesUseCase;
    @Captor
    private ArgumentCaptor<String> jsonCaptor;

    private RadarBroadcaster broadcaster;

    @BeforeEach
    void setUp() {
        broadcaster = new RadarBroadcaster(redisListenerContainer, redisTemplate, new ObjectMapper(), getTopTechnologiesUseCase);
    }

    @Test
    void publishLatestSnapshot_broadcastsTop4AndTop10OverRedis() {
        when(getTopTechnologiesUseCase.execute(4)).thenReturn(Flux.just(
                new TechSnapshot("Kotlin", 120, 45.0, 32.0, 40)));
        when(getTopTechnologiesUseCase.execute(10)).thenReturn(Flux.just(
                new TechSnapshot("Kotlin", 120, 45.0, 32.0, 40),
                new TechSnapshot("Rust", 80, 20.0, 5.0, 10)));
        when(redisTemplate.convertAndSend(eq(CHANNEL), anyString())).thenReturn(Mono.just(1L));

        StepVerifier.create(broadcaster.publishLatestSnapshot()).verifyComplete();

        verify(redisTemplate).convertAndSend(eq(CHANNEL), jsonCaptor.capture());
        assertThat(jsonCaptor.getValue())
                .contains("\"top4\"")
                .contains("\"top10\"")
                .contains("Kotlin")
                .contains("Rust");
    }

    @Test
    void publishLatestSnapshot_queriesBothLimitsEvenWhenOneIsEmpty() {
        when(getTopTechnologiesUseCase.execute(4)).thenReturn(Flux.empty());
        when(getTopTechnologiesUseCase.execute(10)).thenReturn(Flux.empty());
        when(redisTemplate.convertAndSend(eq(CHANNEL), anyString())).thenReturn(Mono.just(1L));

        StepVerifier.create(broadcaster.publishLatestSnapshot()).verifyComplete();

        verify(redisTemplate).convertAndSend(eq(CHANNEL), jsonCaptor.capture());
        assertThat(jsonCaptor.getValue()).contains("\"top4\":[]").contains("\"top10\":[]");
    }
}
