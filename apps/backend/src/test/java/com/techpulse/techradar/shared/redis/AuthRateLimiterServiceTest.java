package com.techpulse.techradar.shared.redis;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthRateLimiterServiceTest {

    @Mock
    private ReactiveStringRedisTemplate redisTemplate;
    @Mock
    private ReactiveValueOperations<String, String> valueOperations;

    private AuthRateLimiterService rateLimiter;

    @BeforeEach
    void setUp() {
        rateLimiter = new AuthRateLimiterService(redisTemplate);
        ReflectionTestUtils.setField(rateLimiter, "loginMaxRequests", 10);
        ReflectionTestUtils.setField(rateLimiter, "loginWindowSeconds", 60L);
        ReflectionTestUtils.setField(rateLimiter, "registerMaxRequests", 5);
        ReflectionTestUtils.setField(rateLimiter, "registerWindowSeconds", 60L);
        ReflectionTestUtils.setField(rateLimiter, "forgotPasswordMaxRequests", 5);
        ReflectionTestUtils.setField(rateLimiter, "forgotPasswordWindowSeconds", 300L);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void firstRequestInWindow_setsExpiryAndIsAllowed() {
        when(valueOperations.increment(anyString())).thenReturn(Mono.just(1L));
        when(redisTemplate.expire(anyString(), any(Duration.class))).thenReturn(Mono.just(true));

        StepVerifier.create(rateLimiter.isLoginAllowed("1.2.3.4"))
                .expectNext(true)
                .verifyComplete();

        verify(redisTemplate).expire("ratelimit:auth:login:1.2.3.4", Duration.ofSeconds(60));
    }

    @Test
    void requestUnderLimit_isAllowed_withoutResettingExpiry() {
        when(valueOperations.increment("ratelimit:auth:register:1.2.3.4")).thenReturn(Mono.just(5L));

        StepVerifier.create(rateLimiter.isRegisterAllowed("1.2.3.4"))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    void requestOverLimit_isRejected() {
        when(valueOperations.increment("ratelimit:auth:register:1.2.3.4")).thenReturn(Mono.just(6L));

        StepVerifier.create(rateLimiter.isRegisterAllowed("1.2.3.4"))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    void differentActions_useIndependentCounters() {
        when(valueOperations.increment("ratelimit:auth:forgot-password:9.9.9.9")).thenReturn(Mono.just(1L));
        when(redisTemplate.expire(anyString(), any(Duration.class))).thenReturn(Mono.just(true));

        StepVerifier.create(rateLimiter.isForgotPasswordAllowed("9.9.9.9"))
                .expectNext(true)
                .verifyComplete();

        verify(redisTemplate).expire("ratelimit:auth:forgot-password:9.9.9.9", Duration.ofSeconds(300));
    }
}
