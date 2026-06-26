package com.techpulse.techradar.shared.redis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Sliding-window rate limiter for the chat API backed by Redis INCR + EXPIRE.
 * Key pattern: ratelimit:chat:<userId>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ChatRateLimiterService {

    private static final String PREFIX = "ratelimit:chat:";

    private final ReactiveStringRedisTemplate redisTemplate;

    @Value("${app.redis.chat-rate-limit.max-requests:20}")
    private int maxRequests;

    @Value("${app.redis.chat-rate-limit.window-seconds:60}")
    private long windowSeconds;

    /**
     * Returns true if the request is allowed; false if the rate limit is exceeded.
     */
    public Mono<Boolean> isAllowed(String userId) {
        String key = PREFIX + userId;
        return redisTemplate.opsForValue().increment(key)
                .flatMap(count -> {
                    if (count == 1) {
                        // First request in window — set expiry
                        return redisTemplate.expire(key, Duration.ofSeconds(windowSeconds))
                                .thenReturn(true);
                    }
                    boolean allowed = count <= maxRequests;
                    if (!allowed) {
                        log.warn("Chat rate limit exceeded userId={} count={} max={}", userId, count, maxRequests);
                    }
                    return Mono.just(allowed);
                });
    }
}
