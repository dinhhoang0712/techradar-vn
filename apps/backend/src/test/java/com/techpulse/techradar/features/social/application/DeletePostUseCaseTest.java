package com.techpulse.techradar.features.social.application;

import com.techpulse.techradar.features.social.ports.PostRepository;
import com.techpulse.techradar.shared.exception.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeletePostUseCaseTest {

    @Mock
    private PostRepository postRepository;

    private DeletePostUseCase useCase;

    private final UUID postId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        useCase = new DeletePostUseCase(postRepository);
    }

    @Test
    void execute_completesWhenThePostIsDeleted() {
        when(postRepository.deleteOwnedBy(postId, userId)).thenReturn(Mono.just(true));

        StepVerifier.create(useCase.execute(postId.toString(), userId.toString())).verifyComplete();

        verify(postRepository).deleteOwnedBy(postId, userId);
    }

    @Test
    void execute_errorsWithNotFoundWhenThePostDoesNotExist() {
        when(postRepository.deleteOwnedBy(postId, userId)).thenReturn(Mono.just(false));

        StepVerifier.create(useCase.execute(postId.toString(), userId.toString()))
                .expectError(NotFoundException.class)
                .verify();
    }

    @Test
    void execute_errorsWithNotFoundWhenThePostIsOwnedBySomeoneElse() {
        // deleteOwnedBy scopes the delete to the given owner, so someone else's post looks
        // identical to a missing one from this use case's point of view: same false -> 404.
        UUID someoneElsesPost = UUID.randomUUID();
        when(postRepository.deleteOwnedBy(someoneElsesPost, userId)).thenReturn(Mono.just(false));

        StepVerifier.create(useCase.execute(someoneElsesPost.toString(), userId.toString()))
                .expectErrorMatches(error -> error instanceof NotFoundException
                        && error.getMessage().contains(someoneElsesPost.toString()))
                .verify();
    }
}
