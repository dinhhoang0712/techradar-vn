package com.techpulse.techradar.features.messaging.realtime;

import com.techpulse.techradar.features.messaging.domain.DirectMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-memory pub/sub for live message delivery over SSE (one connection per online user, fanning
 * out to however many tabs that user has open). Single backend instance only (per docker-compose)
 * — a horizontally-scaled deployment would need Redis pub/sub instead.
 */
@Slf4j
@Component
public class MessageBroadcaster {

    private final Map<String, UserChannel> channels = new ConcurrentHashMap<>();

    private static final class UserChannel {
        final Sinks.Many<DirectMessage> sink = Sinks.many().multicast().onBackpressureBuffer();
        final AtomicInteger subscribers = new AtomicInteger(0);
    }

    /** Subscribes the given user to their own live message stream (call once per SSE connection). */
    public Flux<DirectMessage> subscribe(String userId) {
        UserChannel channel = channels.computeIfAbsent(userId, id -> new UserChannel());
        channel.subscribers.incrementAndGet();
        return channel.sink.asFlux()
                .doFinally(signal -> {
                    if (channel.subscribers.decrementAndGet() <= 0) {
                        channels.remove(userId, channel);
                    }
                });
    }

    /** Pushes a message to a user's live stream, if they currently have one open. No-op otherwise. */
    public void publish(String userId, DirectMessage message) {
        UserChannel channel = channels.get(userId);
        if (channel == null) {
            return;
        }
        Sinks.EmitResult result = channel.sink.tryEmitNext(message);
        if (result.isFailure()) {
            log.warn("Failed to emit live message to user {}: {}", userId, result);
        }
    }
}
