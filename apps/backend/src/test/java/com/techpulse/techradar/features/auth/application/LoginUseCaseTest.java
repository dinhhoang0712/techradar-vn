package com.techpulse.techradar.features.auth.application;

import com.techpulse.techradar.features.auth.domain.User;
import com.techpulse.techradar.features.auth.ports.UserRepository;
import com.techpulse.techradar.shared.exception.InvalidCredentialsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoginUseCaseTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private TokenIssuer tokenIssuer;

    private LoginUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new LoginUseCase(userRepository, passwordEncoder, tokenIssuer);
    }

    private User activeUser() {
        return User.builder()
                .id(UUID.randomUUID())
                .email("dev@example.com")
                .passwordHash("hashed")
                .role("user")
                .status("active")
                .build();
    }

    @Test
    void execute_returnsTokens_whenCredentialsValid() {
        User user = activeUser();
        LoginResponse response = LoginResponse.builder()
                .accessToken("access").refreshToken("refresh")
                .userId(user.getId().toString()).email(user.getEmail()).role(user.getRole())
                .expiresIn(3600L)
                .build();
        when(userRepository.findByEmail("dev@example.com")).thenReturn(Mono.just(user));
        when(passwordEncoder.matches("correct", "hashed")).thenReturn(true);
        when(tokenIssuer.issueFor(user)).thenReturn(Mono.just(response));

        StepVerifier.create(useCase.execute(LoginRequest.builder().email("dev@example.com").password("correct").build()))
                .expectNext(response)
                .verifyComplete();
    }

    @Test
    void execute_fails_whenEmailNotFound() {
        when(userRepository.findByEmail(anyString())).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute(LoginRequest.builder().email("ghost@example.com").password("x").build()))
                .expectError(InvalidCredentialsException.class)
                .verify();
    }

    @Test
    void execute_fails_whenPasswordWrong() {
        User user = activeUser();
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Mono.just(user));
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        StepVerifier.create(useCase.execute(LoginRequest.builder().email(user.getEmail()).password("wrong").build()))
                .expectError(InvalidCredentialsException.class)
                .verify();
    }

    @Test
    void execute_fails_whenUserInactive() {
        User inactive = User.builder()
                .id(UUID.randomUUID()).email("dev@example.com").passwordHash("hashed")
                .role("user").status("blocked")
                .build();
        when(userRepository.findByEmail(inactive.getEmail())).thenReturn(Mono.just(inactive));
        when(passwordEncoder.matches("correct", "hashed")).thenReturn(true);

        StepVerifier.create(useCase.execute(LoginRequest.builder().email(inactive.getEmail()).password("correct").build()))
                .expectError(InvalidCredentialsException.class)
                .verify();
    }
}
