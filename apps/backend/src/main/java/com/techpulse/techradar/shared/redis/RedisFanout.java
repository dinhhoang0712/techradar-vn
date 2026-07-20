package com.techpulse.techradar.shared.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.ReactiveSubscription;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.function.Consumer;

/**
 * Generic cross-instance fan-out over Redis Pub/Sub for features that push live events into a
 * local reactive sink (SSE-style streams): every backend instance (including the publisher's own)
 * subscribes to the given channel, deserializes each message as {@code T}, and hands it to a local
 * callback for delivery to whichever local subscribers that instance is holding — so delivery
 * works regardless of which instance the sender/recipient landed on.
 * <p>
 * Fire-and-forget by design: a publish failure, or an event that fails to deserialize, is logged
 * and dropped, never thrown — matching the semantics of the hand-rolled per-feature versions of
 * this (message and notification live streams) that this generalizes.
 */
@Slf4j
public final class RedisFanout {

    private static final RedisSerializationContext.SerializationPair<String> STRING_PAIR =
            RedisSerializationContext.SerializationPair.fromSerializer(RedisSerializer.string());

    private RedisFanout() {
    }

    /**
     * Subscribes to {@code channel}, invoking {@code onEvent} for every message that successfully
     * deserializes as {@code eventType}. Intended to be called once, typically from a
     * {@code @PostConstruct} method of the calling feature.
     */
    public static <T> void subscribe(ReactiveRedisMessageListenerContainer redisListenerContainer,
                                      ObjectMapper objectMapper,
                                      String channel,
                                      Class<T> eventType,
                                      Consumer<T> onEvent) {
        redisListenerContainer.receive(List.of(ChannelTopic.of(channel)), STRING_PAIR, STRING_PAIR)
                .map(ReactiveSubscription.Message::getMessage)
                .flatMap(json -> Mono.fromCallable(() -> objectMapper.readValue(json, eventType))
                        .onErrorResume(e -> {
                            log.warn("Could not parse live event from Redis channel {}", channel, e);
                            return Mono.empty();
                        }))
                .doOnNext(onEvent)
                .subscribe();
    }

    /** Serializes {@code event} and publishes it to {@code channel}, across all backend instances. */
    public static <T> void publish(ReactiveStringRedisTemplate redisTemplate,
                                    ObjectMapper objectMapper,
                                    String channel,
                                    T event) {
        try {
            String json = objectMapper.writeValueAsString(event);
            redisTemplate.convertAndSend(channel, json)
                    .doOnError(e -> log.warn("Failed to publish live event to Redis channel {}", channel, e))
                    .subscribe();
        } catch (Exception e) {
            log.warn("Failed to serialize live event for Redis channel {}", channel, e);
        }
    }
}
