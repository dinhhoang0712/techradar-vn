package com.techpulse.techradar.shared.redis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Tracks each user's current security stamp in Redis so an admin role/status change (or a
 * password change) can invalidate that user's already-issued access tokens immediately, instead
 * of waiting for natural token expiry (see JwtReactiveAuthenticationManager).
 * Key pattern: security-stamp:<userId>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SecurityStampService {

    private static final String PREFIX = "security-stamp:";

    private final ReactiveStringRedisTemplate redisTemplate;

    public Mono<Void> set(String userId, UUID stamp) {
        return redisTemplate.opsForValue()
                .set(PREFIX + userId, stamp.toString())
                .doOnSuccess(ok -> log.debug("Security stamp updated for userId={}", userId))
                .then();
    }

    /** @return the current stamp for the user, or empty if none has ever been recorded. */
    public Mono<String> currentStamp(String userId) {
        return redisTemplate.opsForValue().get(PREFIX + userId);
    }
}
