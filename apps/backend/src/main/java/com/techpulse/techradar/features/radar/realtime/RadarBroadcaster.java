package com.techpulse.techradar.features.radar.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techpulse.techradar.features.radar.adapters.input.RadarDtos;
import com.techpulse.techradar.features.radar.application.GetTopTechnologiesUseCase;
import com.techpulse.techradar.shared.redis.RedisFanout;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.util.concurrent.Queues;

import java.util.List;

/**
 * Live radar delivery over SSE, mirroring
 * {@link com.techpulse.techradar.features.social.realtime.FeedBroadcaster} and
 * {@link com.techpulse.techradar.features.notification.application.NotificationService}:
 * {@link #publishLatestSnapshot()} always goes over Redis Pub/Sub (channel {@value #CHANNEL}) so
 * every backend instance — not just the one whose ETL run just completed — feeds its own local
 * {@link Sinks.Many} and can deliver to whichever SSE clients ({@link #stream()}) are connected
 * to it. Unlike the feed/notification/message streams, radar data isn't user-scoped: every
 * viewer of {@code GET /radar/stream} sees the same top4/top10 snapshot.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RadarBroadcaster {

    private static final String CHANNEL = "live:radar";
    private static final int TOP4_LIMIT = 4;
    private static final int TOP10_LIMIT = 10;

    private final ReactiveRedisMessageListenerContainer redisListenerContainer;
    private final ReactiveStringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final GetTopTechnologiesUseCase getTopTechnologiesUseCase;

    // autoCancel=false: if every dashboard viewer disconnects momentarily, the default
    // autoCancel=true would terminate this sink and silently refuse every subscriber for the
    // rest of the process's lifetime.
    private final Sinks.Many<RadarSnapshotEvent> sink =
            Sinks.many().multicast().onBackpressureBuffer(Queues.SMALL_BUFFER_SIZE, false);

    @PostConstruct
    void subscribeToRedis() {
        RedisFanout.subscribe(redisListenerContainer, objectMapper, CHANNEL, RadarSnapshotEvent.class, sink::tryEmitNext);
    }

    public Flux<RadarSnapshotEvent> stream() {
        return sink.asFlux();
    }

    /**
     * Recomputes top4/top10 (the caller must evict the radar cache first, so this reads fresh
     * data) and broadcasts the snapshot to every connected dashboard. Called after the
     * {@code tech_analytics} ETL rebuild, both scheduled and admin-triggered.
     */
    public Mono<Void> publishLatestSnapshot() {
        Mono<List<RadarDtos.Top4Item>> top4 = getTopTechnologiesUseCase.execute(TOP4_LIMIT)
                .map(RadarDtos.Top4Item::from)
                .collectList();
        Mono<List<RadarDtos.Top10Item>> top10 = getTopTechnologiesUseCase.execute(TOP10_LIMIT)
                .map(RadarDtos.Top10Item::from)
                .collectList();
        return Mono.zip(top4, top10)
                .doOnNext(tuple -> publish(new RadarSnapshotEvent(tuple.getT1(), tuple.getT2())))
                .then();
    }

    private void publish(RadarSnapshotEvent event) {
        RedisFanout.publish(redisTemplate, objectMapper, CHANNEL, event);
    }
}
