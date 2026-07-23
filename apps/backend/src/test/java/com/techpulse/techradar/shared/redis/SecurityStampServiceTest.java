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

import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SecurityStampServiceTest {

    @Mock
    private ReactiveStringRedisTemplate redisTemplate;
    @Mock
    private ReactiveValueOperations<String, String> valueOperations;

    private SecurityStampService service;

    @BeforeEach
    void setUp() {
        service = new SecurityStampService(redisTemplate);
    }

    @Test
    void set_writesStampUnderUserScopedKey() {
        String userId = "user-1";
        UUID stamp = UUID.randomUUID();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.set("security-stamp:" + userId, stamp.toString())).thenReturn(Mono.just(true));

        StepVerifier.create(service.set(userId, stamp)).verifyComplete();

        verify(valueOperations).set("security-stamp:" + userId, stamp.toString());
    }

    @Test
    void currentStamp_returnsStoredValue_whenPresent() {
        String userId = "user-1";
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("security-stamp:" + userId)).thenReturn(Mono.just("stamp-value"));

        StepVerifier.create(service.currentStamp(userId)).expectNext("stamp-value").verifyComplete();
    }

    @Test
    void currentStamp_returnsEmpty_whenNeverRecorded() {
        String userId = "user-1";
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("security-stamp:" + userId)).thenReturn(Mono.empty());

        StepVerifier.create(service.currentStamp(userId)).verifyComplete();
    }
}
