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
import reactor.util.concurrent.Queues;

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
        // autoCancel=false: a user closing their last tab must not permanently kill this sink —
        // with the default autoCancel=true, the sink terminates once its subscriber count hits
        // zero and silently refuses every subsequent subscribe() for this same UserChannel object.
        final Sinks.Many<DirectMessage> sink = Sinks.many().multicast().onBackpressureBuffer(Queues.SMALL_BUFFER_SIZE, false);
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
        // compute() (not computeIfAbsent() + a separate incrementAndGet()) so a concurrent
        // subscribe() for the same user can never observe the gap between "get-or-create the
        // channel" and "record this subscriber on it" — that gap is exactly what let a resubscribe
        // race the previous subscription's cleanup below and get silently dropped from the map.
        UserChannel channel = channels.compute(userId, (id, existing) -> {
            UserChannel target = existing != null ? existing : new UserChannel();
            target.subscribers.incrementAndGet();
            return target;
        });
        return channel.sink.asFlux()
                .doFinally(signal -> channels.computeIfPresent(userId, (id, current) -> {
                    if (current != channel) {
                        // A newer subscription already replaced this one for this user; it isn't
                        // ours to decrement or remove.
                        return current;
                    }
                    return current.subscribers.decrementAndGet() <= 0 ? null : current;
                }));
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
