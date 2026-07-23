package com.techpulse.techradar.features.user.application;

import com.techpulse.techradar.features.auth.domain.User;
import com.techpulse.techradar.features.auth.ports.UserRepository;
import com.techpulse.techradar.shared.exception.InvalidCredentialsException;
import com.techpulse.techradar.shared.exception.NotFoundException;
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
class DeleteAccountUseCaseTest {

    @Mock
    private UserAccountValidator accountValidator;
    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private SecurityStampService securityStampService;

    private DeleteAccountUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new DeleteAccountUseCase(accountValidator, userRepository, passwordEncoder, securityStampService);
    }

    @Test
    void execute_revokesStampThenDeletesUser_whenPasswordCorrect() {
        UUID userId = UUID.randomUUID();
        User user = User.builder().id(userId).passwordHash("hashed").build();
        when(accountValidator.findByIdOrThrow(userId.toString())).thenReturn(Mono.just(user));
        when(passwordEncoder.matches("correct-password", "hashed")).thenReturn(true);
        when(securityStampService.set(eq(userId.toString()), any(UUID.class))).thenReturn(Mono.empty());
        when(userRepository.deleteById(userId.toString())).thenReturn(Mono.just(1L));

        StepVerifier.create(useCase.execute(userId.toString(), "correct-password")).verifyComplete();

        verify(securityStampService).set(eq(userId.toString()), any(UUID.class));
        verify(userRepository).deleteById(userId.toString());
    }

    @Test
    void execute_rejectsWrongPassword_withoutRevokingOrDeleting() {
        UUID userId = UUID.randomUUID();
        User user = User.builder().id(userId).passwordHash("hashed").build();
        when(accountValidator.findByIdOrThrow(userId.toString())).thenReturn(Mono.just(user));
        when(passwordEncoder.matches("wrong-password", "hashed")).thenReturn(false);

        StepVerifier.create(useCase.execute(userId.toString(), "wrong-password"))
                .expectError(InvalidCredentialsException.class)
                .verify();

        verify(securityStampService, never()).set(any(), any());
        verify(userRepository, never()).deleteById(any());
    }

    @Test
    void execute_propagatesNotFound_whenUserMissing() {
        String userId = UUID.randomUUID().toString();
        when(accountValidator.findByIdOrThrow(userId)).thenReturn(Mono.error(new NotFoundException("User not found")));

        StepVerifier.create(useCase.execute(userId, "any-password"))
                .expectError(NotFoundException.class)
                .verify();
    }
}
