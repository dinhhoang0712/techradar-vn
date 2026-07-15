package com.techpulse.techradar.features.messaging.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techpulse.techradar.features.messaging.domain.DirectMessage;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.ReactiveSubscription;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.util.List;
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
    private static final RedisSerializationContext.SerializationPair<String> STRING_PAIR =
            RedisSerializationContext.SerializationPair.fromSerializer(RedisSerializer.string());

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
        redisListenerContainer.receive(List.of(ChannelTopic.of(CHANNEL)), STRING_PAIR, STRING_PAIR)
                .map(ReactiveSubscription.Message::getMessage)
                .flatMap(json -> Mono.fromCallable(() -> objectMapper.readValue(json, LiveMessageEvent.class))
                        .onErrorResume(e -> {
                            log.warn("Could not parse live message event from Redis", e);
                            return Mono.empty();
                        }))
                .doOnNext(event -> deliverLocally(event.userId(), event.message()))
                .subscribe();
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
        try {
            String json = objectMapper.writeValueAsString(new LiveMessageEvent(userId, message));
            redisTemplate.convertAndSend(CHANNEL, json)
                    .doOnError(e -> log.warn("Failed to publish live message to Redis for user {}", userId, e))
                    .subscribe();
        } catch (Exception e) {
            log.warn("Failed to serialize live message for user {}", userId, e);
        }
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
