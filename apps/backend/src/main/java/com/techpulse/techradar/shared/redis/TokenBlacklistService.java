package com.techpulse.techradar.shared.redis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Manages a Redis-backed blacklist for invalidated JWT tokens.
 * Key pattern: blacklist:token:<jti-or-token-hash>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TokenBlacklistService {

    private static final String PREFIX = "blacklist:token:";

    private final ReactiveStringRedisTemplate redisTemplate;

    public Mono<Void> blacklist(String token, Duration ttl) {
        String key = PREFIX + token.hashCode();
        return redisTemplate.opsForValue()
                .set(key, "1", ttl)
                .doOnSuccess(ok -> log.debug("Token blacklisted key={} ttl={}s", key, ttl.getSeconds()))
                .then();
    }

    public Mono<Boolean> isBlacklisted(String token) {
        String key = PREFIX + token.hashCode();
        return redisTemplate.hasKey(key);
    }
}
