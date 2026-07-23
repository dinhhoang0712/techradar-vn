package com.techpulse.techradar.features.user.application;

import com.techpulse.techradar.features.auth.domain.User;
import com.techpulse.techradar.features.auth.ports.UserRepository;
import com.techpulse.techradar.shared.exception.ConflictException;
import com.techpulse.techradar.shared.exception.NotFoundException;
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
class UserAccountValidatorTest {

    @Mock
    private UserRepository userRepository;

    private UserAccountValidator validator;

    @BeforeEach
    void setUp() {
        validator = new UserAccountValidator(userRepository);
    }

    @Test
    void findByIdOrThrow_returnsUser_whenFound() {
        User user = User.builder().id(UUID.randomUUID()).email("dev@example.com").build();
        when(userRepository.findById(user.getId().toString())).thenReturn(Mono.just(user));

        StepVerifier.create(validator.findByIdOrThrow(user.getId().toString()))
                .expectNext(user)
                .verifyComplete();
    }

    @Test
    void findByIdOrThrow_throwsNotFound_whenMissing() {
        String userId = UUID.randomUUID().toString();
        when(userRepository.findById(userId)).thenReturn(Mono.empty());

        StepVerifier.create(validator.findByIdOrThrow(userId))
                .expectError(NotFoundException.class)
                .verify();
    }

    @Test
    void validateEmailUnique_completes_whenEmailNotRegistered() {
        when(userRepository.findByEmail("new@example.com")).thenReturn(Mono.empty());

        StepVerifier.create(validator.validateEmailUnique("new@example.com", null)).verifyComplete();
    }

    @Test
    void validateEmailUnique_completes_whenEmailBelongsToCurrentUser() {
        User self = User.builder().id(UUID.randomUUID()).email("me@example.com").build();
        when(userRepository.findByEmail("me@example.com")).thenReturn(Mono.just(self));

        StepVerifier.create(validator.validateEmailUnique("me@example.com", self.getId().toString()))
                .verifyComplete();
    }

    @Test
    void validateEmailUnique_throwsConflict_whenEmailBelongsToAnotherUser() {
        User other = User.builder().id(UUID.randomUUID()).email("taken@example.com").build();
        when(userRepository.findByEmail("taken@example.com")).thenReturn(Mono.just(other));

        StepVerifier.create(validator.validateEmailUnique("taken@example.com", UUID.randomUUID().toString()))
                .expectError(ConflictException.class)
                .verify();
    }

    @Test
    void validateEmailUnique_throwsConflict_whenCurrentUserIdIsNullButEmailTaken() {
        User other = User.builder().id(UUID.randomUUID()).email("taken@example.com").build();
        when(userRepository.findByEmail("taken@example.com")).thenReturn(Mono.just(other));

        StepVerifier.create(validator.validateEmailUnique("taken@example.com", null))
                .expectError(ConflictException.class)
                .verify();
    }
}
