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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TokenBlacklistServiceTest {

    @Mock
    private ReactiveStringRedisTemplate redisTemplate;
    @Mock
    private ReactiveValueOperations<String, String> valueOperations;

    private TokenBlacklistService service;

    @BeforeEach
    void setUp() {
        service = new TokenBlacklistService(redisTemplate);
    }

    @Test
    void blacklist_setsKeyDerivedFromTokenHashCodeWithGivenTtl() {
        String token = "some.jwt.token";
        String expectedKey = "blacklist:token:" + token.hashCode();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.set(eq(expectedKey), eq("1"), any(Duration.class))).thenReturn(Mono.just(true));

        StepVerifier.create(service.blacklist(token, Duration.ofSeconds(3600))).verifyComplete();

        verify(valueOperations).set(expectedKey, "1", Duration.ofSeconds(3600));
    }

    @Test
    void isBlacklisted_checksKeyDerivedFromTokenHashCode() {
        String token = "some.jwt.token";
        String expectedKey = "blacklist:token:" + token.hashCode();
        when(redisTemplate.hasKey(expectedKey)).thenReturn(Mono.just(true));

        StepVerifier.create(service.isBlacklisted(token)).expectNext(true).verifyComplete();
    }

    @Test
    void isBlacklisted_returnsFalse_whenKeyNotPresent() {
        String token = "another.jwt.token";
        String expectedKey = "blacklist:token:" + token.hashCode();
        when(redisTemplate.hasKey(expectedKey)).thenReturn(Mono.just(false));

        StepVerifier.create(service.isBlacklisted(token)).expectNext(false).verifyComplete();
    }

    @Test
    void differentTokens_produceDifferentKeys() {
        String tokenA = "token-a";
        String tokenB = "token-b";

        org.assertj.core.api.Assertions.assertThat(tokenA.hashCode()).isNotEqualTo(tokenB.hashCode());
    }
}
