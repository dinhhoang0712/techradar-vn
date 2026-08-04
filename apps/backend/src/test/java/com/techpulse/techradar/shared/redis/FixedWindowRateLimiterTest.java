package com.techpulse.techradar.shared.redis;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the core INCR+EXPIRE logic directly — the 3 callers (Auth/Chat/AiProxy
 * RateLimiterService) each only pin their own key-prefix/config wiring, trusting this class for
 * the shared behavior.
 */
@ExtendWith(MockitoExtension.class)
class FixedWindowRateLimiterTest {

    @Mock
    private ReactiveStringRedisTemplate redisTemplate;
    @Mock
    private ReactiveValueOperations<String, String> valueOperations;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void isAllowed_firstRequestInWindow_setsExpiryAndIsAllowed() {
        when(valueOperations.increment("key")).thenReturn(Mono.just(1L));
        when(redisTemplate.expire(anyString(), any(Duration.class))).thenReturn(Mono.just(true));

        StepVerifier.create(FixedWindowRateLimiter.isAllowed(redisTemplate, "key", 5, 60))
                .expectNext(true)
                .verifyComplete();

        verify(redisTemplate).expire("key", Duration.ofSeconds(60));
    }

    @Test
    void isAllowed_requestUnderLimit_isAllowed_withoutResettingExpiry() {
        when(valueOperations.increment("key")).thenReturn(Mono.just(5L));

        StepVerifier.create(FixedWindowRateLimiter.isAllowed(redisTemplate, "key", 5, 60))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    void isAllowed_requestOverLimit_isRejected() {
        when(valueOperations.increment("key")).thenReturn(Mono.just(6L));

        StepVerifier.create(FixedWindowRateLimiter.isAllowed(redisTemplate, "key", 5, 60))
                .expectNext(false)
                .verifyComplete();
    }
}
