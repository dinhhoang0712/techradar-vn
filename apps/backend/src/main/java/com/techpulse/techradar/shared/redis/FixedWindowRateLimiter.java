package com.techpulse.techradar.shared.redis;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Fixed-window rate limiter core (Redis INCR + EXPIRE), shared by every per-feature limiter
 * (auth/chat/aiproxy) — each of those used to reimplement this exact increment/expire/compare
 * block by hand, differing only in key prefix and log message text. Static (not a
 * {@code @Component}) so the 3 callers keep injecting {@link ReactiveStringRedisTemplate}
 * directly and their existing tests don't need to switch to mocking a new bean.
 */
@Slf4j
public final class FixedWindowRateLimiter {

    private FixedWindowRateLimiter() {
    }

    /** True if allowed; false if {@code key} already hit {@code maxRequests} within {@code windowSeconds}. */
    public static Mono<Boolean> isAllowed(
            ReactiveStringRedisTemplate redisTemplate, String key, int maxRequests, long windowSeconds) {
        return redisTemplate.opsForValue().increment(key)
                .flatMap(count -> {
                    if (count == 1) {
                        // First request in window — set expiry
                        return redisTemplate.expire(key, Duration.ofSeconds(windowSeconds))
                                .thenReturn(true);
                    }
                    boolean allowed = count <= maxRequests;
                    if (!allowed) {
                        log.warn("Rate limit exceeded key={} count={} max={}", key, count, maxRequests);
                    }
                    return Mono.just(allowed);
                });
    }
}
