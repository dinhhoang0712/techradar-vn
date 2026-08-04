package com.techpulse.techradar.shared.sse;

import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

import java.time.Duration;

/**
 * Merges an SSE event stream with a periodic {@code comment("ping")}, so a reverse proxy/load
 * balancer that times out idle connections doesn't silently kill a long-lived stream. This exact
 * {@code Flux.interval + comment + Flux.merge} block used to be copy-pasted at every SSE endpoint
 * (Radar/Feed/Notification) — and was missing entirely from a 4th (Conversation), a latent bug
 * where its stream could go idle-timed-out with no visible error.
 */
public final class SseHeartbeat {

    private static final Duration DEFAULT_INTERVAL = Duration.ofSeconds(25);

    private SseHeartbeat() {
    }

    public static <T> Flux<ServerSentEvent<T>> merge(Flux<ServerSentEvent<T>> events) {
        return merge(events, DEFAULT_INTERVAL);
    }

    public static <T> Flux<ServerSentEvent<T>> merge(Flux<ServerSentEvent<T>> events, Duration interval) {
        Flux<ServerSentEvent<T>> heartbeat = Flux.interval(interval)
                .map(i -> ServerSentEvent.<T>builder().comment("ping").build());
        return Flux.merge(events, heartbeat);
    }
}
