package com.techpulse.techradar.features.messaging.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techpulse.techradar.features.messaging.domain.DirectMessage;
import com.techpulse.techradar.shared.redis.RedisFanout;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Live message delivery over SSE (one connection per online user, fanning out to however many
 * tabs that user has open). publish() always goes over Redis Pub/Sub (channel {@value #CHANNEL})
 * rather than writing the local sink directly — every backend instance (including the publisher's
 * own) is subscribed to that channel and delivers to whichever local SSE subscribers it's
 * holding, so this works regardless of which instance the sender/recipient landed on.
 * <p>
 * Fire-and-forget by design, same as the in-memory sink this replaced: Postgres remains the
 * source of truth for messages, so a missed live push just means the recipient sees it on their
 * next {@code GET /conversations/{id}/messages} instead of instantly.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MessageBroadcaster {

    private static final String CHANNEL = "live:messages";

    private final Map<String, UserChannel> channels = new ConcurrentHashMap<>();
    private final ReactiveRedisMessageListenerContainer redisListenerContainer;
    private final ReactiveStringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private static final class UserChannel {
        final Sinks.Many<DirectMessage> sink = Sinks.many().multicast().onBackpressureBuffer();
        final AtomicInteger subscribers = new AtomicInteger(0);
    }

    private record LiveMessageEvent(String userId, DirectMessage message) {
    }

    @PostConstruct
    void subscribeToRedis() {
        RedisFanout.subscribe(redisListenerContainer, objectMapper, CHANNEL, LiveMessageEvent.class,
                event -> deliverLocally(event.userId(), event.message()));
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

    /** Publishes a message to a user's live stream, across all backend instances. */
    public void publish(String userId, DirectMessage message) {
        RedisFanout.publish(redisTemplate, objectMapper, CHANNEL, new LiveMessageEvent(userId, message));
    }

    private void deliverLocally(String userId, DirectMessage message) {
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
