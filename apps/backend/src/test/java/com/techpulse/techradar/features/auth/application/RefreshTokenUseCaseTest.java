package com.techpulse.techradar.features.auth.application;

import com.techpulse.techradar.features.auth.domain.User;
import com.techpulse.techradar.features.auth.ports.TokenValidator;
import com.techpulse.techradar.features.auth.ports.UserRepository;
import com.techpulse.techradar.shared.exception.InvalidCredentialsException;
import com.techpulse.techradar.shared.redis.TokenBlacklistService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenUseCaseTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private TokenValidator tokenValidator;
    @Mock
    private TokenBlacklistService tokenBlacklist;
    @Mock
    private TokenIssuer tokenIssuer;

    private RefreshTokenUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new RefreshTokenUseCase(userRepository, tokenValidator, tokenBlacklist, tokenIssuer);
    }

    @Test
    void execute_fails_whenTokenBlacklisted() {
        when(tokenBlacklist.isBlacklisted("token")).thenReturn(Mono.just(true));

        StepVerifier.create(useCase.execute("token"))
                .expectError(InvalidCredentialsException.class)
                .verify();
    }

    @Test
    void execute_fails_whenTokenInvalid() {
        when(tokenBlacklist.isBlacklisted("token")).thenReturn(Mono.just(false));
        when(tokenValidator.isValid("token")).thenReturn(false);

        StepVerifier.create(useCase.execute("token"))
                .expectError(InvalidCredentialsException.class)
                .verify();
    }

    @Test
    void execute_fails_whenTokenIsNotRefreshToken() {
        when(tokenBlacklist.isBlacklisted("token")).thenReturn(Mono.just(false));
        when(tokenValidator.isValid("token")).thenReturn(true);
        when(tokenValidator.isRefreshToken("token")).thenReturn(false);

        StepVerifier.create(useCase.execute("token"))
                .expectError(InvalidCredentialsException.class)
                .verify();
    }

    @Test
    void execute_fails_whenUserNoLongerExists() {
        when(tokenBlacklist.isBlacklisted("token")).thenReturn(Mono.just(false));
        when(tokenValidator.isValid("token")).thenReturn(true);
        when(tokenValidator.isRefreshToken("token")).thenReturn(true);
        when(tokenValidator.extractUserId("token")).thenReturn("missing-user");
        when(userRepository.findById("missing-user")).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute("token"))
                .expectError(InvalidCredentialsException.class)
                .verify();
    }

    @Test
    void execute_issuesNewTokens_whenRefreshTokenValid() {
        User user = User.builder().id(UUID.randomUUID()).email("dev@example.com").role("user").status("ACTIVE").build();
        LoginResponse response = LoginResponse.builder().accessToken("new-access").build();
        when(tokenBlacklist.isBlacklisted("token")).thenReturn(Mono.just(false));
        when(tokenValidator.isValid("token")).thenReturn(true);
        when(tokenValidator.isRefreshToken("token")).thenReturn(true);
        when(tokenValidator.extractUserId("token")).thenReturn(user.getId().toString());
        when(userRepository.findById(user.getId().toString())).thenReturn(Mono.just(user));
        when(tokenIssuer.issueFor(user)).thenReturn(Mono.just(response));

        StepVerifier.create(useCase.execute("token"))
                .expectNext(response)
                .verifyComplete();
    }

    @Test
    void execute_fails_whenUserIsInactive_evenWithAValidUnexpiredRefreshToken() {
        // Guards against a banned/deactivated user minting fresh access tokens forever via
        // /auth/refresh, bypassing the ban until their refresh token naturally expires.
        User banned = User.builder().id(UUID.randomUUID()).email("dev@example.com").role("user").status("SUSPENDED").build();
        when(tokenBlacklist.isBlacklisted("token")).thenReturn(Mono.just(false));
        when(tokenValidator.isValid("token")).thenReturn(true);
        when(tokenValidator.isRefreshToken("token")).thenReturn(true);
        when(tokenValidator.extractUserId("token")).thenReturn(banned.getId().toString());
        when(userRepository.findById(banned.getId().toString())).thenReturn(Mono.just(banned));

        StepVerifier.create(useCase.execute("token"))
                .expectError(InvalidCredentialsException.class)
                .verify();
    }
}
