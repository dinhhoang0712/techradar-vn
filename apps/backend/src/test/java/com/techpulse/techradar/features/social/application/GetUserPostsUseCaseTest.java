package com.techpulse.techradar.features.social.application;

import com.techpulse.techradar.features.social.domain.FeedPost;
import com.techpulse.techradar.features.social.domain.UserSummary;
import com.techpulse.techradar.features.social.ports.PostRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetUserPostsUseCaseTest {

    @Mock
    private PostRepository postRepository;

    private GetUserPostsUseCase useCase;

    private final UUID targetUserId = UUID.randomUUID();
    private final UUID viewerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        useCase = new GetUserPostsUseCase(postRepository);
    }

    @Test
    void execute_mapsFeedRowsToFeedPostAsSeenByTheViewer() {
        UUID postId = UUID.randomUUID();
        LocalDateTime createdAt = LocalDateTime.of(2026, 7, 20, 9, 30);
        PostRepository.FeedRow row = new PostRepository.FeedRow(
                postId, targetUserId, "An Nguyen", "avatar.png", "Hello world", createdAt,
                4L, 2L, true, List.of(), List.of("java"), null, null, null
        );
        when(postRepository.findByUser(targetUserId, viewerId, 20, 0)).thenReturn(Flux.just(row));

        StepVerifier.create(useCase.execute(targetUserId.toString(), viewerId.toString(), 0, 20))
                .expectNext(new FeedPost(
                        postId.toString(),
                        new UserSummary(targetUserId.toString(), "An Nguyen", "avatar.png"),
                        "Hello world",
                        createdAt,
                        4L,
                        2L,
                        true,
                        List.of(),
                        List.of("java"),
                        null
                ))
                .verifyComplete();
    }

    @Test
    void execute_returnsEmptyWhenTheUserHasNoPosts() {
        when(postRepository.findByUser(eq(targetUserId), eq(viewerId), anyInt(), anyInt())).thenReturn(Flux.empty());

        StepVerifier.create(useCase.execute(targetUserId.toString(), viewerId.toString(), 0, 20)).verifyComplete();
    }

    @Test
    void execute_defaultsSizeWhenNonPositive() {
        when(postRepository.findByUser(any(), any(), eq(20), anyInt())).thenReturn(Flux.empty());

        useCase.execute(targetUserId.toString(), viewerId.toString(), 0, 0).blockLast();

        verify(postRepository).findByUser(targetUserId, viewerId, 20, 0);
    }

    @Test
    void execute_clampsSizeToTheMax() {
        when(postRepository.findByUser(any(), any(), eq(50), anyInt())).thenReturn(Flux.empty());

        useCase.execute(targetUserId.toString(), viewerId.toString(), 0, 999).blockLast();

        verify(postRepository).findByUser(targetUserId, viewerId, 50, 0);
        verify(postRepository, never()).findByUser(targetUserId, viewerId, 999, 0);
    }

    @Test
    void execute_computesOffsetFromThePageNumber() {
        when(postRepository.findByUser(any(), any(), anyInt(), eq(40))).thenReturn(Flux.empty());

        useCase.execute(targetUserId.toString(), viewerId.toString(), 2, 20).blockLast();

        verify(postRepository).findByUser(targetUserId, viewerId, 20, 40);
    }
}
