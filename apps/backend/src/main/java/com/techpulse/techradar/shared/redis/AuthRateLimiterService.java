package com.techpulse.techradar.shared.redis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Sliding-window rate limiter for unauthenticated auth endpoints (login/register/forgot-password),
 * backed by Redis INCR + EXPIRE. Keyed by client IP (there's no user id yet at this point) rather
 * than the {@code ratelimit:chat:<userId>} scheme {@link ChatRateLimiterService} uses.
 * Key pattern: ratelimit:auth:<action>:<ip>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AuthRateLimiterService {

    private static final String PREFIX = "ratelimit:auth:";

    private final ReactiveStringRedisTemplate redisTemplate;

    @Value("${app.redis.auth-rate-limit.login.max-requests:10}")
    private int loginMaxRequests;
    @Value("${app.redis.auth-rate-limit.login.window-seconds:60}")
    private long loginWindowSeconds;

    @Value("${app.redis.auth-rate-limit.register.max-requests:5}")
    private int registerMaxRequests;
    @Value("${app.redis.auth-rate-limit.register.window-seconds:60}")
    private long registerWindowSeconds;

    @Value("${app.redis.auth-rate-limit.forgot-password.max-requests:5}")
    private int forgotPasswordMaxRequests;
    @Value("${app.redis.auth-rate-limit.forgot-password.window-seconds:300}")
    private long forgotPasswordWindowSeconds;

    public Mono<Boolean> isLoginAllowed(String ip) {
        return isAllowed("login", ip, loginMaxRequests, loginWindowSeconds);
    }

    public Mono<Boolean> isRegisterAllowed(String ip) {
        return isAllowed("register", ip, registerMaxRequests, registerWindowSeconds);
    }

    public Mono<Boolean> isForgotPasswordAllowed(String ip) {
        return isAllowed("forgot-password", ip, forgotPasswordMaxRequests, forgotPasswordWindowSeconds);
    }

    private Mono<Boolean> isAllowed(String action, String ip, int maxRequests, long windowSeconds) {
        String key = PREFIX + action + ":" + ip;
        return redisTemplate.opsForValue().increment(key)
                .flatMap(count -> {
                    if (count == 1) {
                        // First request in window — set expiry
                        return redisTemplate.expire(key, Duration.ofSeconds(windowSeconds))
                                .thenReturn(true);
                    }
                    boolean allowed = count <= maxRequests;
                    if (!allowed) {
                        log.warn("Auth rate limit exceeded action={} ip={} count={} max={}",
                                action, ip, count, maxRequests);
                    }
                    return Mono.just(allowed);
                });
    }
}
