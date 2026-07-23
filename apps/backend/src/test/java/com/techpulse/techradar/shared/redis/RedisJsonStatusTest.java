package com.techpulse.techradar.shared.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisJsonStatusTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private ReactiveStringRedisTemplate redisTemplate;
    @Mock
    private ReactiveValueOperations<String, String> valueOperations;

    private record Status(String state) {
    }

    @Test
    void write_serializesAndSetsTheKey() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.set(eq("status:key"), eq("{\"state\":\"idle\"}"))).thenReturn(Mono.just(true));

        StepVerifier.create(RedisJsonStatus.write(redisTemplate, objectMapper, "status:key", new Status("idle")))
                .verifyComplete();

        verify(valueOperations).set("status:key", "{\"state\":\"idle\"}");
    }

    @Test
    void write_completesWithoutError_whenRedisSetFails() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.set(eq("status:key"), org.mockito.ArgumentMatchers.any()))
                .thenReturn(Mono.error(new RuntimeException("connection refused")));

        StepVerifier.create(RedisJsonStatus.write(redisTemplate, objectMapper, "status:key", new Status("idle")))
                .verifyComplete();
    }

    @Test
    void read_parsesTheStoredJson() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("status:key")).thenReturn(Mono.just("{\"state\":\"running\"}"));

        StepVerifier.create(RedisJsonStatus.read(redisTemplate, objectMapper, "status:key", Status.class, new Status("default")))
                .expectNext(new Status("running"))
                .verifyComplete();
    }

    @Test
    void read_returnsDefault_whenKeyIsMissing() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("status:key")).thenReturn(Mono.empty());

        StepVerifier.create(RedisJsonStatus.read(redisTemplate, objectMapper, "status:key", Status.class, new Status("default")))
                .expectNext(new Status("default"))
                .verifyComplete();
    }

    @Test
    void read_returnsDefault_whenStoredJsonIsMalformed() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("status:key")).thenReturn(Mono.just("not-valid-json"));

        StepVerifier.create(RedisJsonStatus.read(redisTemplate, objectMapper, "status:key", Status.class, new Status("default")))
                .expectNext(new Status("default"))
                .verifyComplete();
    }

    @Test
    void read_returnsDefault_whenRedisGetFails() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("status:key")).thenReturn(Mono.error(new RuntimeException("connection refused")));

        StepVerifier.create(RedisJsonStatus.read(redisTemplate, objectMapper, "status:key", Status.class, new Status("default")))
                .expectNext(new Status("default"))
                .verifyComplete();
    }
}
