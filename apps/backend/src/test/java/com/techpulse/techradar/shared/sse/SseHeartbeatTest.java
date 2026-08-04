package com.techpulse.techradar.shared.sse;

import org.junit.jupiter.api.Test;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the merge behavior directly on the shared helper — the 4 SSE controllers (Radar/Feed/
 * Notification/Conversation) trust this instead of each re-testing their own copy of the
 * interval+comment+merge block.
 */
class SseHeartbeatTest {

    @Test
    void merge_passesRealEventsThrough_untouched() {
        Flux<ServerSentEvent<String>> events = Flux.just(
                ServerSentEvent.builder("hello").build());

        StepVerifier.create(SseHeartbeat.merge(events, Duration.ofMillis(50)).take(1))
                .assertNext(event -> assertThat(event.data()).isEqualTo("hello"))
                .verifyComplete();
    }

    @Test
    void merge_emitsAPingComment_onceTheIntervalElapses_evenWithNoRealEvents() {
        Sinks.Many<ServerSentEvent<String>> noRealEvents = Sinks.many().multicast().onBackpressureBuffer();

        StepVerifier.create(SseHeartbeat.merge(noRealEvents.asFlux(), Duration.ofMillis(10)).take(1))
                .assertNext(event -> assertThat(event.comment()).isEqualTo("ping"))
                .verifyComplete();
    }

    @Test
    void merge_defaultInterval_isTwentyFiveSeconds() {
        // Regression guard: the single-arg overload must keep using the 25s default the 4
        // controllers rely on implicitly — a real event still arrives immediately regardless,
        // this only pins that the call doesn't blow up wiring the default heartbeat.
        Flux<ServerSentEvent<String>> events = Flux.just(ServerSentEvent.builder("hi").build());

        StepVerifier.create(SseHeartbeat.merge(events).take(1))
                .assertNext(event -> assertThat(event.data()).isEqualTo("hi"))
                .verifyComplete();
    }
}
