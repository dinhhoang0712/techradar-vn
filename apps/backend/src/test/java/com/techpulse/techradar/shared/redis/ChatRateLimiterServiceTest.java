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
class ChatRateLimiterServiceTest {

    @Mock
    private ReactiveStringRedisTemplate redisTemplate;
    @Mock
    private ReactiveValueOperations<String, String> valueOperations;

    private ChatRateLimiterService rateLimiter;

    @BeforeEach
    void setUp() {
        rateLimiter = new ChatRateLimiterService(redisTemplate);
        ReflectionTestUtils.setField(rateLimiter, "maxRequests", 20);
        ReflectionTestUtils.setField(rateLimiter, "windowSeconds", 60L);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void isAllowed_firstRequestInWindow_setsExpiryAndIsAllowed() {
        when(valueOperations.increment("ratelimit:chat:user-1")).thenReturn(Mono.just(1L));
        when(redisTemplate.expire(anyString(), any(Duration.class))).thenReturn(Mono.just(true));

        StepVerifier.create(rateLimiter.isAllowed("user-1")).expectNext(true).verifyComplete();

        verify(redisTemplate).expire("ratelimit:chat:user-1", Duration.ofSeconds(60));
    }

    @Test
    void isAllowed_requestUnderLimit_isAllowed() {
        when(valueOperations.increment("ratelimit:chat:user-1")).thenReturn(Mono.just(20L));

        StepVerifier.create(rateLimiter.isAllowed("user-1")).expectNext(true).verifyComplete();
    }

    @Test
    void isAllowed_requestOverLimit_isRejected() {
        when(valueOperations.increment("ratelimit:chat:user-1")).thenReturn(Mono.just(21L));

        StepVerifier.create(rateLimiter.isAllowed("user-1")).expectNext(false).verifyComplete();
    }

    @Test
    void isAllowed_usesPerUserKey() {
        when(valueOperations.increment("ratelimit:chat:user-2")).thenReturn(Mono.just(5L));

        StepVerifier.create(rateLimiter.isAllowed("user-2")).expectNext(true).verifyComplete();

        verify(valueOperations).increment("ratelimit:chat:user-2");
    }
}
