package com.techpulse.techradar.shared.redis;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisLockTest {

    @Mock
    private ReactiveStringRedisTemplate redisTemplate;
    @Mock
    private ReactiveValueOperations<String, String> valueOperations;

    @Test
    void tryAcquire_delegatesToSetIfAbsentWithExactKeyValueAndTtl() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq("some:lock:key"), eq("1"), eq(Duration.ofSeconds(10))))
                .thenReturn(Mono.just(true));

        StepVerifier.create(RedisLock.tryAcquire(redisTemplate, "some:lock:key", Duration.ofSeconds(10)))
                .expectNext(true)
                .verifyComplete();

        verify(valueOperations).setIfAbsent("some:lock:key", "1", Duration.ofSeconds(10));
    }

    @Test
    void tryAcquire_returnsFalseWhenLockAlreadyHeld() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq("some:lock:key"), eq("1"), eq(Duration.ofSeconds(10))))
                .thenReturn(Mono.just(false));

        StepVerifier.create(RedisLock.tryAcquire(redisTemplate, "some:lock:key", Duration.ofSeconds(10)))
                .expectNext(false)
                .verifyComplete();
    }
}
