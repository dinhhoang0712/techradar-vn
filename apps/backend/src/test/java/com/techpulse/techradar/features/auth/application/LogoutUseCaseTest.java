package com.techpulse.techradar.features.auth.application;

import com.techpulse.techradar.features.auth.ports.TokenValidator;
import com.techpulse.techradar.shared.redis.TokenBlacklistService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LogoutUseCaseTest {

    private static final long MAX_TTL_SECONDS = 604800L;

    @Mock
    private TokenBlacklistService blacklist;
    @Mock
    private TokenValidator tokenValidator;

    private LogoutUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new LogoutUseCase(blacklist, tokenValidator);
        ReflectionTestUtils.setField(useCase, "maxTtlSeconds", MAX_TTL_SECONDS);
    }

    @Test
    void execute_blacklistsToken_withRemainingTokenTtl() {
        when(tokenValidator.expirationTimeMillis("token")).thenReturn(System.currentTimeMillis() + 10_000);
        when(blacklist.blacklist(eq("token"), org.mockito.ArgumentMatchers.any())).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute("token")).verifyComplete();

        ArgumentCaptor<Duration> captor = ArgumentCaptor.forClass(Duration.class);
        verify(blacklist).blacklist(eq("token"), captor.capture());
        assertThat(captor.getValue().getSeconds()).isBetween(8L, 10L);
    }

    @Test
    void execute_clampsTtl_toConfiguredMax_whenTokenLifetimeExceedsIt() {
        when(tokenValidator.expirationTimeMillis("token")).thenReturn(System.currentTimeMillis() + Duration.ofDays(30).toMillis());
        when(blacklist.blacklist(eq("token"), org.mockito.ArgumentMatchers.any())).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute("token")).verifyComplete();

        ArgumentCaptor<Duration> captor = ArgumentCaptor.forClass(Duration.class);
        verify(blacklist).blacklist(eq("token"), captor.capture());
        assertThat(captor.getValue().getSeconds()).isEqualTo(MAX_TTL_SECONDS);
    }

    @Test
    void execute_fallsBackToMaxTtl_whenTokenValidatorThrows() {
        when(tokenValidator.expirationTimeMillis("bad-token")).thenThrow(new RuntimeException("malformed token"));
        when(blacklist.blacklist(eq("bad-token"), org.mockito.ArgumentMatchers.any())).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute("bad-token")).verifyComplete();

        ArgumentCaptor<Duration> captor = ArgumentCaptor.forClass(Duration.class);
        verify(blacklist).blacklist(eq("bad-token"), captor.capture());
        assertThat(captor.getValue().getSeconds()).isEqualTo(MAX_TTL_SECONDS);
    }
}
