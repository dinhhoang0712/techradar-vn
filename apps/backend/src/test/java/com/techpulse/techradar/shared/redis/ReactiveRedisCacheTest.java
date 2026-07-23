package com.techpulse.techradar.shared.redis;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReactiveRedisCacheTest {

    private static final TypeReference<List<String>> LIST_TYPE = new TypeReference<>() {};

    @Mock
    private ReactiveStringRedisTemplate redisTemplate;
    @Mock
    private ReactiveValueOperations<String, String> valueOperations;

    private ReactiveRedisCache cache;

    @BeforeEach
    void setUp() {
        cache = new ReactiveRedisCache(redisTemplate, new ObjectMapper());
    }

    @Test
    void getOrLoad_returnsCachedItems_onHit_withoutCallingLoader() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("key")).thenReturn(Mono.just("[\"a\",\"b\"]"));
        Flux<String> loader = Flux.error(new IllegalStateException("loader must not be called on a hit"));

        StepVerifier.create(cache.getOrLoad("key", Duration.ofSeconds(60), loader, LIST_TYPE))
                .expectNext("a", "b")
                .verifyComplete();
    }

    @Test
    void getOrLoad_callsLoaderAndCachesResult_onMiss() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("key")).thenReturn(Mono.empty());
        when(valueOperations.set(eq("key"), any(), any(Duration.class))).thenReturn(Mono.just(true));

        StepVerifier.create(cache.getOrLoad("key", Duration.ofSeconds(60), Flux.just("x", "y"), LIST_TYPE))
                .expectNext("x", "y")
                .verifyComplete();

        verify(valueOperations).set(eq("key"), eq("[\"x\",\"y\"]"), eq(Duration.ofSeconds(60)));
    }

    @Test
    void getOrLoad_fallsBackToLoader_whenCachedJsonIsMalformed() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("key")).thenReturn(Mono.just("not-valid-json"));
        when(valueOperations.set(eq("key"), any(), any(Duration.class))).thenReturn(Mono.just(true));

        StepVerifier.create(cache.getOrLoad("key", Duration.ofSeconds(60), Flux.just("fresh"), LIST_TYPE))
                .expectNext("fresh")
                .verifyComplete();
    }

    @Test
    void getOrLoadMono_returnsCachedValue_onHit_withoutCallingLoader() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("key")).thenReturn(Mono.just("\"cached-value\""));
        Mono<String> loader = Mono.error(new IllegalStateException("loader must not be called on a hit"));

        StepVerifier.create(cache.getOrLoadMono("key", Duration.ofSeconds(60), loader, new TypeReference<String>() {}))
                .expectNext("cached-value")
                .verifyComplete();
    }

    @Test
    void getOrLoadMono_callsLoaderAndCachesResult_onMiss() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("key")).thenReturn(Mono.empty());
        when(valueOperations.set(eq("key"), any(), any(Duration.class))).thenReturn(Mono.just(true));

        StepVerifier.create(cache.getOrLoadMono("key", Duration.ofSeconds(60), Mono.just("fresh-value"),
                        new TypeReference<String>() {}))
                .expectNext("fresh-value")
                .verifyComplete();

        verify(valueOperations).set(eq("key"), eq("\"fresh-value\""), eq(Duration.ofSeconds(60)));
    }

    @Test
    void evict_deletesTheKey() {
        when(redisTemplate.delete("key")).thenReturn(Mono.just(1L));

        StepVerifier.create(cache.evict("key")).verifyComplete();

        verify(redisTemplate).delete("key");
    }

    @Test
    void evictByPattern_deletesEveryMatchingKey() {
        when(redisTemplate.keys("cache:job:match:*")).thenReturn(Flux.just("cache:job:match:a", "cache:job:match:b"));
        when(redisTemplate.delete("cache:job:match:a")).thenReturn(Mono.just(1L));
        when(redisTemplate.delete("cache:job:match:b")).thenReturn(Mono.just(1L));

        StepVerifier.create(cache.evictByPattern("cache:job:match:*")).verifyComplete();

        verify(redisTemplate).delete("cache:job:match:a");
        verify(redisTemplate).delete("cache:job:match:b");
    }

    @Test
    void evictByPattern_isANoOp_whenNoKeysMatch() {
        when(redisTemplate.keys("cache:nothing:*")).thenReturn(Flux.empty());

        StepVerifier.create(cache.evictByPattern("cache:nothing:*")).verifyComplete();

        verify(redisTemplate, never()).delete(any(String.class));
    }
}
