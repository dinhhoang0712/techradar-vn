package com.techpulse.techradar.shared.redis;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

/**
 * Generic reactive Redis cache backed by JSON strings.
 * get() returns cached Flux<T> on hit; on miss it calls the loader, collects to a list,
 * caches the JSON, then re-emits the items.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ReactiveRedisCache {

    private final ReactiveStringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Returns cached Flux<T> or populates the cache from the loader Flux.
     */
    public <T> Flux<T> getOrLoad(String key, Duration ttl, Flux<T> loader, TypeReference<List<T>> typeRef) {
        return redisTemplate.opsForValue().get(key)
                .flatMapMany(json -> {
                    try {
                        List<T> items = objectMapper.readValue(json, typeRef);
                        log.debug("Cache HIT key={}", key);
                        return Flux.fromIterable(items);
                    } catch (Exception e) {
                        log.warn("Cache deserialization failed key={}, reloading", key);
                        return loadAndCache(key, ttl, loader, typeRef);
                    }
                })
                .switchIfEmpty(loadAndCache(key, ttl, loader, typeRef));
    }

    /**
     * Returns cached Mono<T> or populates the cache from the loader Mono.
     */
    public <T> Mono<T> getOrLoadMono(String key, Duration ttl, Mono<T> loader, TypeReference<T> typeRef) {
        return redisTemplate.opsForValue().get(key)
                .flatMap(json -> {
                    try {
                        T value = objectMapper.readValue(json, typeRef);
                        log.debug("Cache HIT key={}", key);
                        return Mono.just(value);
                    } catch (Exception e) {
                        log.warn("Cache deserialization failed key={}, reloading", key);
                        return loadAndCacheMono(key, ttl, loader, typeRef);
                    }
                })
                .switchIfEmpty(loadAndCacheMono(key, ttl, loader, typeRef));
    }

    public Mono<Void> evict(String key) {
        return redisTemplate.delete(key).then();
    }

    public Mono<Void> evictByPattern(String pattern) {
        return redisTemplate.keys(pattern)
                .flatMap(redisTemplate::delete)
                .then();
    }

    private <T> Flux<T> loadAndCache(String key, Duration ttl, Flux<T> loader, TypeReference<List<T>> typeRef) {
        return loader.collectList()
                .flatMap(items -> {
                    try {
                        String json = objectMapper.writeValueAsString(items);
                        return redisTemplate.opsForValue().set(key, json, ttl)
                                .doOnSuccess(ok -> log.debug("Cache SET key={} ttl={}s items={}", key, ttl.getSeconds(), items.size()))
                                .thenReturn(items);
                    } catch (Exception e) {
                        log.warn("Cache serialization failed key={}", key, e);
                        return Mono.just(items);
                    }
                })
                .flatMapMany(Flux::fromIterable);
    }

    private <T> Mono<T> loadAndCacheMono(String key, Duration ttl, Mono<T> loader, TypeReference<T> typeRef) {
        return loader.flatMap(value -> {
            try {
                String json = objectMapper.writeValueAsString(value);
                return redisTemplate.opsForValue().set(key, json, ttl)
                        .doOnSuccess(ok -> log.debug("Cache SET key={} ttl={}s", key, ttl.getSeconds()))
                        .thenReturn(value);
            } catch (Exception e) {
                log.warn("Cache serialization failed key={}", key, e);
                return Mono.just(value);
            }
        });
    }
}
