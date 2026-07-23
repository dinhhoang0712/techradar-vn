package com.techpulse.techradar.shared.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import reactor.core.publisher.Mono;

/**
 * A single JSON-valued Redis key used as a small cross-instance status board — the same shape
 * {@link com.techpulse.techradar.features.system.adapters.input.CrawlerAdminController} hand-rolled
 * for {@code crawler:status} (this generalizes it for new call sites; the crawler's own read path
 * is left as-is). {@link #write} is fire-and-forget:
 * a serialization or Redis failure is logged and swallowed rather than failing whatever real work
 * the status update was reporting on. {@link #read} degrades to {@code defaultValue} the same way
 * (missing key, malformed JSON, or a Redis error), so a caller never has to null-check.
 */
@Slf4j
public final class RedisJsonStatus {

    private RedisJsonStatus() {
    }

    public static <T> Mono<Void> write(ReactiveStringRedisTemplate template, ObjectMapper mapper, String key, T value) {
        return Mono.defer(() -> {
            try {
                String json = mapper.writeValueAsString(value);
                return template.opsForValue().set(key, json).then();
            } catch (Exception e) {
                log.warn("Could not write status to Redis key {}", key, e);
                return Mono.empty();
            }
        }).onErrorResume(e -> {
            log.warn("Could not write status to Redis key {}", key, e);
            return Mono.empty();
        });
    }

    public static <T> Mono<T> read(ReactiveStringRedisTemplate template, ObjectMapper mapper, String key,
                                    Class<T> type, T defaultValue) {
        return Mono.defer(() -> template.opsForValue().get(key))
                .flatMap(json -> {
                    try {
                        return Mono.just(mapper.readValue(json, type));
                    } catch (Exception e) {
                        log.warn("Could not parse status from Redis key {}", key, e);
                        return Mono.just(defaultValue);
                    }
                })
                .defaultIfEmpty(defaultValue)
                .onErrorReturn(defaultValue);
    }
}
