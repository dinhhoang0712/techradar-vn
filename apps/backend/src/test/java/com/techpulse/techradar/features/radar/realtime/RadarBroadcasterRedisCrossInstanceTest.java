package com.techpulse.techradar.features.radar.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techpulse.techradar.features.radar.adapters.input.RadarDtos;
import com.techpulse.techradar.features.radar.application.GetTopTechnologiesUseCase;
import com.techpulse.techradar.features.radar.domain.TechSnapshot;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Same proof as {@link com.techpulse.techradar.features.notification.application.NotificationServiceRedisCrossInstanceTest}
 * and {@link com.techpulse.techradar.features.messaging.realtime.MessageBroadcasterRedisCrossInstanceTest}, for radar:
 * two independent {@link RadarBroadcaster} objects (standing in for two backend replicas) sharing
 * one real Redis. {@link RadarBroadcaster#publishLatestSnapshot()} on "instance A" (the replica
 * whose ETL run just completed) must reach a {@link RadarBroadcaster#stream()} subscriber held by
 * "instance B" (a replica just serving a dashboard's SSE connection).
 */
@EnabledIfEnvironmentVariable(named = "REDIS_HOST", matches = ".+")
class RadarBroadcasterRedisCrossInstanceTest {

    private static LettuceConnectionFactory factoryA;
    private static LettuceConnectionFactory factoryB;
    private static RadarBroadcaster instanceA;
    private static RadarBroadcaster instanceB;

    @BeforeAll
    static void setUp() {
        String host = System.getenv("REDIS_HOST");
        int port = Integer.parseInt(System.getenv().getOrDefault("REDIS_PORT", "6379"));

        ObjectMapper objectMapper = new ObjectMapper();
        GetTopTechnologiesUseCase stubUseCase = mock(GetTopTechnologiesUseCase.class);
        when(stubUseCase.execute(anyInt()))
                .thenReturn(Flux.just(new TechSnapshot("Kotlin", 120, 45.0, 32.0, 40)));

        factoryA = newConnectionFactory(host, port);
        factoryB = newConnectionFactory(host, port);

        instanceA = newBroadcaster(stubUseCase, factoryA, objectMapper);
        instanceB = newBroadcaster(stubUseCase, factoryB, objectMapper);
    }

    @AfterAll
    static void tearDown() {
        factoryA.destroy();
        factoryB.destroy();
    }

    private static LettuceConnectionFactory newConnectionFactory(String host, int port) {
        LettuceConnectionFactory factory = new LettuceConnectionFactory(host, port);
        factory.afterPropertiesSet();
        return factory;
    }

    private static RadarBroadcaster newBroadcaster(GetTopTechnologiesUseCase useCase,
                                                    ReactiveRedisConnectionFactory connectionFactory,
                                                    ObjectMapper objectMapper) {
        ReactiveRedisMessageListenerContainer container = new ReactiveRedisMessageListenerContainer(connectionFactory);
        ReactiveStringRedisTemplate template = new ReactiveStringRedisTemplate(connectionFactory);
        RadarBroadcaster broadcaster = new RadarBroadcaster(container, template, objectMapper, useCase);
        broadcaster.subscribeToRedis();
        return broadcaster;
    }

    @Test
    void publishLatestSnapshotOnOneInstance_isDeliveredToAStreamSubscriberOnAnotherInstance() {
        // "instance B" holds the dashboard's SSE stream.
        Flux<RadarSnapshotEvent> received = instanceB.stream();

        StepVerifier.create(received.take(1).timeout(Duration.ofSeconds(5)))
                .then(() -> {
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    // "instance A" is where the ETL rebuild landed.
                    instanceA.publishLatestSnapshot().subscribe();
                })
                .assertNext(snapshot -> {
                    assertThat(snapshot.getTop4()).extracting(RadarDtos.Top4Item::getIndustry).containsExactly("Kotlin");
                    assertThat(snapshot.getTop10()).extracting(RadarDtos.Top10Item::getKeyword).containsExactly("Kotlin");
                })
                .verifyComplete();
    }
}
