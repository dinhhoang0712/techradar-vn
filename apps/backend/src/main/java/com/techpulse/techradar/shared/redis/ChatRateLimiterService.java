package com.techpulse.techradar.shared.redis;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Sliding-window rate limiter for the chat API, backed by Redis INCR + EXPIRE (core logic in
 * {@link FixedWindowRateLimiter}).
 * Key pattern: ratelimit:chat:<userId>
 */
@Service
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
        return FixedWindowRateLimiter.isAllowed(redisTemplate, PREFIX + userId, maxRequests, windowSeconds);
    }
}
