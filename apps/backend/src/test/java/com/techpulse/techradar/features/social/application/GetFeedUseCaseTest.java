package com.techpulse.techradar.features.social.application;

import com.techpulse.techradar.features.social.ports.PostRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetFeedUseCaseTest {

    @Mock
    private PostRepository postRepository;

    private GetFeedUseCase useCase;

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        useCase = new GetFeedUseCase(postRepository);
    }

    @Test
    void execute_defaultsToFollowingFeedWhenScopeIsBlankOrUnrecognized() {
        when(postRepository.findFeed(any(), any(), anyInt(), anyInt())).thenReturn(Flux.empty());

        StepVerifier.create(useCase.execute(userId.toString(), null, null, 0, 20)).verifyComplete();
        StepVerifier.create(useCase.execute(userId.toString(), "", null, 0, 20)).verifyComplete();
        StepVerifier.create(useCase.execute(userId.toString(), "bogus", null, 0, 20)).verifyComplete();

        verify(postRepository, never()).findExplore(any(), any(), anyInt(), anyInt());
    }

    @Test
    void execute_usesExploreFeedWhenScopeIsExplore() {
        when(postRepository.findExplore(any(), any(), anyInt(), anyInt())).thenReturn(Flux.empty());

        StepVerifier.create(useCase.execute(userId.toString(), "explore", null, 0, 20)).verifyComplete();

        verify(postRepository, never()).findFeed(any(), any(), anyInt(), anyInt());
    }

    @Test
    void execute_lowercasesAndTrimsTheHashtagFilter() {
        when(postRepository.findFeed(any(), any(), anyInt(), anyInt())).thenReturn(Flux.empty());

        useCase.execute(userId.toString(), "following", "  Java ", 0, 20).blockLast();

        verify(postRepository).findFeed(userId, "java", 20, 0);
    }

    @Test
    void execute_treatsBlankHashtagAsNoFilter() {
        when(postRepository.findFeed(any(), any(), anyInt(), anyInt())).thenReturn(Flux.empty());

        useCase.execute(userId.toString(), "following", "   ", 0, 20).blockLast();

        verify(postRepository).findFeed(userId, null, 20, 0);
    }
}
