package com.techpulse.techradar.features.auth.application;

import com.techpulse.techradar.features.auth.domain.User;
import com.techpulse.techradar.features.auth.ports.UserRepository;
import com.techpulse.techradar.shared.exception.ConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegisterUseCaseTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private TokenIssuer tokenIssuer;

    private RegisterUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new RegisterUseCase(userRepository, passwordEncoder, tokenIssuer);
    }

    @Test
    void execute_fails_whenEmailAlreadyRegistered() {
        when(userRepository.existsByEmail("dev@example.com")).thenReturn(Mono.just(true));

        RegisterRequest request = RegisterRequest.builder()
                .email("dev@example.com").password("password123").fullName("Dev").build();

        StepVerifier.create(useCase.execute(request))
                .expectError(ConflictException.class)
                .verify();
    }

    @Test
    void execute_createsUser_withDefaultFreeTier_whenTierNotProvided() {
        when(userRepository.existsByEmail("new@example.com")).thenReturn(Mono.just(false));
        when(passwordEncoder.encode("password123")).thenReturn("hashed");
        when(userRepository.save(any())).thenAnswer(inv -> {
            User saved = inv.getArgument(0);
            saved.setId(UUID.randomUUID());
            return Mono.just(saved);
        });
        LoginResponse response = LoginResponse.builder().accessToken("a").refreshToken("r").build();
        when(tokenIssuer.issueFor(any())).thenReturn(Mono.just(response));

        RegisterRequest request = RegisterRequest.builder()
                .email("new@example.com").password("password123").fullName("New Dev").build();

        StepVerifier.create(useCase.execute(request))
                .expectNext(response)
                .verifyComplete();

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getSubscriptionTier()).isEqualTo("free");
        assertThat(captor.getValue().getRole()).isEqualTo("user");
        assertThat(captor.getValue().getStatus()).isEqualTo("active");
        assertThat(captor.getValue().getPasswordHash()).isEqualTo("hashed");
    }

    @Test
    void execute_createsUser_withProvidedTier() {
        when(userRepository.existsByEmail("paid@example.com")).thenReturn(Mono.just(false));
        when(passwordEncoder.encode(any())).thenReturn("hashed");
        when(userRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(tokenIssuer.issueFor(any())).thenReturn(Mono.just(LoginResponse.builder().build()));

        RegisterRequest request = RegisterRequest.builder()
                .email("paid@example.com").password("password123").fullName("Paid Dev")
                .subscriptionTier("pro").build();

        StepVerifier.create(useCase.execute(request)).expectNextCount(1).verifyComplete();

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getSubscriptionTier()).isEqualTo("pro");
    }
}
