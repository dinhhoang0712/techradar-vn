package com.techpulse.techradar.features.auth.application;

import com.techpulse.techradar.features.auth.domain.User;
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
class GetCurrentUserUseCaseTest {

    @Mock
    private UserRepository userRepository;

    private GetCurrentUserUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetCurrentUserUseCase(userRepository);
    }

    @Test
    void execute_returnsUser_whenFound() {
        User user = User.builder().id(UUID.randomUUID()).email("dev@example.com").build();
        when(userRepository.findById(user.getId().toString())).thenReturn(Mono.just(user));

        StepVerifier.create(useCase.execute(user.getId().toString()))
                .expectNext(user)
                .verifyComplete();
    }

    @Test
    void execute_completesEmpty_whenUserNotFound() {
        when(userRepository.findById("missing")).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute("missing")).verifyComplete();
    }

    @Test
    void execute_completesEmpty_withoutQuerying_whenUserIdBlank() {
        StepVerifier.create(useCase.execute("  ")).verifyComplete();

        verify(userRepository, never()).findById(anyString());
    }

    @Test
    void execute_completesEmpty_withoutQuerying_whenUserIdNull() {
        StepVerifier.create(useCase.execute(null)).verifyComplete();

        verify(userRepository, never()).findById(anyString());
    }
}
