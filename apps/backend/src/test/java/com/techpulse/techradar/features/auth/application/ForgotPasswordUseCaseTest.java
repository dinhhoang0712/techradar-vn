package com.techpulse.techradar.features.auth.application;

import com.techpulse.techradar.features.auth.domain.User;
import com.techpulse.techradar.features.auth.ports.EmailSender;
import com.techpulse.techradar.features.auth.ports.PasswordResetRepository;
import com.techpulse.techradar.features.auth.ports.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ForgotPasswordUseCaseTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordResetRepository passwordResetRepository;
    @Mock
    private EmailSender emailSender;

    private ForgotPasswordUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ForgotPasswordUseCase(userRepository, passwordResetRepository, emailSender);
    }

    @Test
    void execute_createsTokenAndSendsEmail_whenAccountExists() {
        User user = User.builder().id(UUID.randomUUID()).email("dev@example.com").build();
        UUID resetToken = UUID.randomUUID();
        when(userRepository.findByEmail("dev@example.com")).thenReturn(Mono.just(user));
        when(passwordResetRepository.createToken(user.getId().toString())).thenReturn(Mono.just(resetToken));
        when(emailSender.sendPasswordReset("dev@example.com", resetToken.toString())).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute("dev@example.com")).verifyComplete();

        verify(emailSender).sendPasswordReset("dev@example.com", resetToken.toString());
    }

    @Test
    void execute_completesSilently_whenNoAccountForEmail() {
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute("ghost@example.com")).verifyComplete();

        verify(passwordResetRepository, never()).createToken(anyString());
        verify(emailSender, never()).sendPasswordReset(anyString(), anyString());
    }

    @Test
    void execute_stillCompletes_whenEmailSendFails() {
        User user = User.builder().id(UUID.randomUUID()).email("dev@example.com").build();
        UUID resetToken = UUID.randomUUID();
        when(userRepository.findByEmail("dev@example.com")).thenReturn(Mono.just(user));
        when(passwordResetRepository.createToken(user.getId().toString())).thenReturn(Mono.just(resetToken));
        when(emailSender.sendPasswordReset("dev@example.com", resetToken.toString()))
                .thenReturn(Mono.error(new RuntimeException("smtp down")));

        StepVerifier.create(useCase.execute("dev@example.com")).verifyComplete();
    }
}
