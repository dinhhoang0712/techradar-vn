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
class AiProxyRateLimiterServiceTest {

    @Mock
    private ReactiveStringRedisTemplate redisTemplate;
    @Mock
    private ReactiveValueOperations<String, String> valueOperations;

    private AiProxyRateLimiterService rateLimiter;

    @BeforeEach
    void setUp() {
        rateLimiter = new AiProxyRateLimiterService(redisTemplate);
        ReflectionTestUtils.setField(rateLimiter, "maxRequests", 20);
        ReflectionTestUtils.setField(rateLimiter, "windowSeconds", 60L);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void isAllowedForUser_firstRequestInWindow_setsExpiryAndIsAllowed() {
        when(valueOperations.increment("ratelimit:aiproxy:user:user-1")).thenReturn(Mono.just(1L));
        when(redisTemplate.expire(anyString(), any(Duration.class))).thenReturn(Mono.just(true));

        StepVerifier.create(rateLimiter.isAllowedForUser("user-1"))
                .expectNext(true)
                .verifyComplete();

        verify(redisTemplate).expire("ratelimit:aiproxy:user:user-1", Duration.ofSeconds(60));
    }

    @Test
    void isAllowedForUser_requestUnderLimit_isAllowed() {
        when(valueOperations.increment("ratelimit:aiproxy:user:user-1")).thenReturn(Mono.just(20L));

        StepVerifier.create(rateLimiter.isAllowedForUser("user-1"))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    void isAllowedForUser_requestOverLimit_isRejected() {
        when(valueOperations.increment("ratelimit:aiproxy:user:user-1")).thenReturn(Mono.just(21L));

        StepVerifier.create(rateLimiter.isAllowedForUser("user-1"))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    void isAllowedForIp_usesDistinctKeyPrefixFromUserId() {
        when(valueOperations.increment("ratelimit:aiproxy:ip:203.0.113.7")).thenReturn(Mono.just(1L));
        when(redisTemplate.expire(anyString(), any(Duration.class))).thenReturn(Mono.just(true));

        StepVerifier.create(rateLimiter.isAllowedForIp("203.0.113.7"))
                .expectNext(true)
                .verifyComplete();

        verify(redisTemplate).expire("ratelimit:aiproxy:ip:203.0.113.7", Duration.ofSeconds(60));
    }

    @Test
    void isAllowedForIp_requestOverLimit_isRejected() {
        when(valueOperations.increment("ratelimit:aiproxy:ip:203.0.113.7")).thenReturn(Mono.just(25L));

        StepVerifier.create(rateLimiter.isAllowedForIp("203.0.113.7"))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    void userAndIpCounters_areIndependent() {
        when(valueOperations.increment("ratelimit:aiproxy:user:user-1")).thenReturn(Mono.just(1L));
        when(valueOperations.increment("ratelimit:aiproxy:ip:203.0.113.7")).thenReturn(Mono.just(1L));
        when(redisTemplate.expire(anyString(), any(Duration.class))).thenReturn(Mono.just(true));

        StepVerifier.create(rateLimiter.isAllowedForUser("user-1")).expectNext(true).verifyComplete();
        StepVerifier.create(rateLimiter.isAllowedForIp("203.0.113.7")).expectNext(true).verifyComplete();

        verify(redisTemplate).expire("ratelimit:aiproxy:user:user-1", Duration.ofSeconds(60));
        verify(redisTemplate).expire("ratelimit:aiproxy:ip:203.0.113.7", Duration.ofSeconds(60));
    }
}
