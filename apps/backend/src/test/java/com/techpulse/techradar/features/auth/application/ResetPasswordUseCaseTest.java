package com.techpulse.techradar.features.auth.application;

import com.techpulse.techradar.features.auth.domain.User;
import com.techpulse.techradar.features.auth.ports.PasswordResetRepository;
import com.techpulse.techradar.features.auth.ports.UserRepository;
import com.techpulse.techradar.shared.exception.BadRequestException;
import com.techpulse.techradar.shared.redis.SecurityStampService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResetPasswordUseCaseTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordResetRepository passwordResetRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private SecurityStampService securityStampService;

    private ResetPasswordUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ResetPasswordUseCase(userRepository, passwordResetRepository, passwordEncoder, securityStampService);
    }

    @Test
    void execute_rejectsShortPassword_withoutTouchingRepositories() {
        StepVerifier.create(useCase.execute(UUID.randomUUID().toString(), "short"))
                .expectError(BadRequestException.class)
                .verify();

        verify(passwordResetRepository, never()).findValidUserId(any());
    }

    @Test
    void execute_rejectsMalformedToken() {
        StepVerifier.create(useCase.execute("not-a-uuid", "longenoughpassword"))
                .expectError(BadRequestException.class)
                .verify();

        verify(passwordResetRepository, never()).findValidUserId(any());
    }

    @Test
    void execute_rejectsNullToken() {
        StepVerifier.create(useCase.execute(null, "longenoughpassword"))
                .expectError(BadRequestException.class)
                .verify();
    }

    @Test
    void execute_fails_whenTokenNotFoundOrExpired() {
        String token = UUID.randomUUID().toString();
        when(passwordResetRepository.findValidUserId(token)).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute(token, "longenoughpassword"))
                .expectError(BadRequestException.class)
                .verify();
    }

    @Test
    void execute_fails_whenUserForTokenNoLongerExists() {
        String token = UUID.randomUUID().toString();
        String userId = UUID.randomUUID().toString();
        when(passwordResetRepository.findValidUserId(token)).thenReturn(Mono.just(userId));
        when(userRepository.findById(userId)).thenReturn(Mono.empty());
        // .then(passwordResetRepository.markUsed(token)) is built eagerly inside the flatMap
        // lambda regardless of the findById outcome above, so it must be stubbed here too even
        // though this branch never actually reaches/subscribes to it.
        when(passwordResetRepository.markUsed(token)).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute(token, "longenoughpassword"))
                .expectError(BadRequestException.class)
                .verify();
    }

    @Test
    void execute_updatesPasswordAndMarksTokenUsed_whenValid() {
        String token = UUID.randomUUID().toString();
        User user = User.builder().id(UUID.randomUUID()).email("dev@example.com").passwordHash("old").build();
        when(passwordResetRepository.findValidUserId(token)).thenReturn(Mono.just(user.getId().toString()));
        when(userRepository.findById(user.getId().toString())).thenReturn(Mono.just(user));
        when(passwordEncoder.encode("newlongpassword")).thenReturn("new-hashed");
        when(userRepository.save(user)).thenReturn(Mono.just(user));
        when(securityStampService.set(eq(user.getId().toString()), any(UUID.class))).thenReturn(Mono.empty());
        when(passwordResetRepository.markUsed(token)).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute(token, "newlongpassword")).verifyComplete();

        verify(userRepository).save(user);
        verify(passwordResetRepository).markUsed(token);
        verify(securityStampService).set(eq(user.getId().toString()), any(UUID.class));
        org.assertj.core.api.Assertions.assertThat(user.getPasswordHash()).isEqualTo("new-hashed");
    }
}
