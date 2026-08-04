package com.techpulse.techradar.shared.redis;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Sliding-window rate limiter for the {@code aiproxy} module (career/recommend/interview/agent/
 * forecast/report/chat-summarize/company-insight — all proxy to expensive LLM calls on
 * ai-rag-core), backed by Redis INCR + EXPIRE (core logic in {@link FixedWindowRateLimiter}), same
 * mechanism as {@link AuthRateLimiterService} and {@link ChatRateLimiterService}. Keyed by user id
 * for the authenticated routes ({@code forwardAsCurrentUser}) and by client IP for the public
 * routes ({@code forward}), since the public routes have no user id to key on.
 * Key patterns: ratelimit:aiproxy:user:<userId>, ratelimit:aiproxy:ip:<ip>
 */
@Service
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
        return FixedWindowRateLimiter.isAllowed(redisTemplate, key, maxRequests, windowSeconds);
    }
}
