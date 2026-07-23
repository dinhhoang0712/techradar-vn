package com.techpulse.techradar.features.social.application;

import com.techpulse.techradar.features.social.domain.PostComment;
import com.techpulse.techradar.features.social.domain.UserSummary;
import com.techpulse.techradar.features.social.ports.CommentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetCommentsUseCaseTest {

    @Mock
    private CommentRepository commentRepository;

    private GetCommentsUseCase useCase;

    private final UUID postId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        useCase = new GetCommentsUseCase(commentRepository);
    }

    @Test
    void execute_mapsTopLevelAndReplyRowsToPostComment() {
        UUID topLevelId = UUID.randomUUID();
        UUID replyId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        LocalDateTime createdAt = LocalDateTime.of(2026, 7, 20, 10, 0);

        when(commentRepository.findByPost(postId, 20, 0)).thenReturn(Flux.just(
                new CommentRepository.CommentRow(topLevelId, authorId, "An Nguyen", "avatar.png", "Nice post", null, createdAt),
                new CommentRepository.CommentRow(replyId, authorId, "An Nguyen", "avatar.png", "Thanks!", topLevelId, createdAt)
        ));

        StepVerifier.create(useCase.execute(postId.toString(), 0, 20))
                .expectNext(new PostComment(
                        topLevelId.toString(),
                        new UserSummary(authorId.toString(), "An Nguyen", "avatar.png"),
                        "Nice post",
                        null,
                        createdAt
                ))
                .expectNext(new PostComment(
                        replyId.toString(),
                        new UserSummary(authorId.toString(), "An Nguyen", "avatar.png"),
                        "Thanks!",
                        topLevelId.toString(),
                        createdAt
                ))
                .verifyComplete();
    }

    @Test
    void execute_returnsEmptyWhenThePostHasNoComments() {
        when(commentRepository.findByPost(eq(postId), anyInt(), anyInt())).thenReturn(Flux.empty());

        StepVerifier.create(useCase.execute(postId.toString(), 0, 20)).verifyComplete();
    }

    @Test
    void execute_defaultsSizeWhenNonPositive() {
        when(commentRepository.findByPost(any(), eq(20), anyInt())).thenReturn(Flux.empty());

        useCase.execute(postId.toString(), 0, 0).blockLast();

        verify(commentRepository).findByPost(postId, 20, 0);
    }

    @Test
    void execute_clampsSizeToTheMax() {
        when(commentRepository.findByPost(any(), eq(100), anyInt())).thenReturn(Flux.empty());

        useCase.execute(postId.toString(), 0, 999).blockLast();

        verify(commentRepository).findByPost(postId, 100, 0);
        verify(commentRepository, never()).findByPost(postId, 999, 0);
    }

    @Test
    void execute_computesOffsetFromThePageNumber() {
        when(commentRepository.findByPost(any(), anyInt(), eq(40))).thenReturn(Flux.empty());

        useCase.execute(postId.toString(), 2, 20).blockLast();

        verify(commentRepository).findByPost(postId, 20, 40);
    }

    @Test
    void execute_treatsANegativePageAsZero() {
        when(commentRepository.findByPost(any(), anyInt(), eq(0))).thenReturn(Flux.empty());

        useCase.execute(postId.toString(), -1, 20).blockLast();

        verify(commentRepository).findByPost(postId, 20, 0);
    }
}
