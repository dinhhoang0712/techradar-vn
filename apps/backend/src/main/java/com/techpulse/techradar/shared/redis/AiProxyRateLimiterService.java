package com.techpulse.techradar.shared.redis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Sliding-window rate limiter for the {@code aiproxy} module (career/recommend/interview/agent/
 * forecast/report/chat-summarize/company-insight — all proxy to expensive LLM calls on
 * ai-rag-core), backed by Redis INCR + EXPIRE, same mechanism as {@link AuthRateLimiterService}
 * and {@link ChatRateLimiterService}. Keyed by user id for the authenticated routes
 * ({@code forwardAsCurrentUser}) and by client IP for the public routes ({@code forward}), since
 * the public routes have no user id to key on.
 * Key patterns: ratelimit:aiproxy:user:<userId>, ratelimit:aiproxy:ip:<ip>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AiProxyRateLimiterService {

    private static final String USER_PREFIX = "ratelimit:aiproxy:user:";
    private static final String IP_PREFIX = "ratelimit:aiproxy:ip:";

    private final ReactiveStringRedisTemplate redisTemplate;

    @Value("${app.redis.aiproxy-rate-limit.max-requests:20}")
    private int maxRequests;

    @Value("${app.redis.aiproxy-rate-limit.window-seconds:60}")
    private long windowSeconds;

    public Mono<Boolean> isAllowedForUser(String userId) {
        return isAllowed(USER_PREFIX + userId);
    }

    public Mono<Boolean> isAllowedForIp(String ip) {
        return isAllowed(IP_PREFIX + ip);
    }

    private Mono<Boolean> isAllowed(String key) {
        return redisTemplate.opsForValue().increment(key)
                .flatMap(count -> {
                    if (count == 1) {
                        // First request in window — set expiry
                        return redisTemplate.expire(key, Duration.ofSeconds(windowSeconds))
                                .thenReturn(true);
                    }
                    boolean allowed = count <= maxRequests;
                    if (!allowed) {
                        log.warn("AI proxy rate limit exceeded key={} count={} max={}", key, count, maxRequests);
                    }
                    return Mono.just(allowed);
                });
    }
}
