package com.techpulse.techradar.shared.redis;

import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Short debounce lock for admin "trigger now" endpoints: {@code SET key val NX EX ttl} is a
 * single atomic Redis command, so two racing requests can never both acquire it. Not a "held for
 * the whole job duration" mutex — callers still track actual in-flight state separately (a
 * status table/key), same as {@code CrawlerAdminController} already did before this was
 * extracted from its original inline SETNX call so the other trigger endpoints
 * ({@code AdminDataPlatformController}, {@code AdminClusteringController}) can share it instead
 * of duplicating or omitting it.
 */
public final class RedisLock {

    private RedisLock() {
    }

    /** True if the lock was acquired (caller should proceed); false if someone else holds it. */
    public static Mono<Boolean> tryAcquire(ReactiveStringRedisTemplate redisTemplate, String key, Duration ttl) {
        return redisTemplate.opsForValue().setIfAbsent(key, "1", ttl);
    }
}
