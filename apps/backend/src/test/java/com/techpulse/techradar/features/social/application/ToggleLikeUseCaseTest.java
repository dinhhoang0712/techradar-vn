package com.techpulse.techradar.features.social.application;

import com.techpulse.techradar.features.notification.application.ActivityNotifier;
import com.techpulse.techradar.features.social.ports.PostRepository;
import com.techpulse.techradar.features.social.realtime.FeedBroadcaster;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ToggleLikeUseCaseTest {

    @Mock
    private PostRepository postRepository;
    @Mock
    private ActivityNotifier activityNotifier;
    @Mock
    private FeedBroadcaster feedBroadcaster;

    private ToggleLikeUseCase useCase;

    private final UUID postId = UUID.randomUUID();
    private final UUID authorId = UUID.randomUUID();
    private final UUID likerId = UUID.randomUUID();

    private PostRepository.FeedRow feedRow(long likeCount) {
        return new PostRepository.FeedRow(
                postId, authorId, "Author", null, "content", LocalDateTime.now(),
                likeCount, 0, false, List.of(), List.of(), null, null, null);
    }

    @BeforeEach
    void setUp() {
        useCase = new ToggleLikeUseCase(postRepository, activityNotifier, feedBroadcaster);
        lenient().when(postRepository.findById(any(), any())).thenReturn(Mono.just(feedRow(1)));
    }

    @Test
    void like_notifiesAuthorOnANewLikeBySomeoneElse() {
        when(postRepository.like(postId, likerId)).thenReturn(Mono.just(true));
        when(postRepository.findAuthorId(postId)).thenReturn(Mono.just(authorId));
        when(activityNotifier.notify(likerId, authorId, "POST_LIKE", "đã thích bài viết của bạn", "/feed"))
                .thenReturn(Mono.empty());

        StepVerifier.create(useCase.like(postId.toString(), likerId.toString())).verifyComplete();

        verify(activityNotifier).notify(likerId, authorId, "POST_LIKE", "đã thích bài viết của bạn", "/feed");
    }

    @Test
    void like_doesNotNotifyOnARepeatedLike() {
        when(postRepository.like(postId, likerId)).thenReturn(Mono.just(false));

        StepVerifier.create(useCase.like(postId.toString(), likerId.toString())).verifyComplete();

        verify(activityNotifier, never()).notify(any(), any(), any(), any(), any());
    }

    @Test
    void like_doesNotNotifyWhenLikingYourOwnPost() {
        when(postRepository.like(postId, likerId)).thenReturn(Mono.just(true));
        when(postRepository.findAuthorId(postId)).thenReturn(Mono.just(likerId));

        StepVerifier.create(useCase.like(postId.toString(), likerId.toString())).verifyComplete();

        verify(activityNotifier, never()).notify(any(), any(), any(), any(), any());
    }

    @Test
    void like_stillSucceedsWhenNotificationLookupFails() {
        when(postRepository.like(postId, likerId)).thenReturn(Mono.just(true));
        when(postRepository.findAuthorId(postId)).thenReturn(Mono.error(new RuntimeException("boom")));

        StepVerifier.create(useCase.like(postId.toString(), likerId.toString())).verifyComplete();
    }

    @Test
    void like_broadcastsUpdatedLikeCountOnANewLike() {
        when(postRepository.like(postId, likerId)).thenReturn(Mono.just(true));
        when(postRepository.findAuthorId(postId)).thenReturn(Mono.just(likerId));
        when(postRepository.findById(postId, likerId)).thenReturn(Mono.just(feedRow(7)));

        useCase.like(postId.toString(), likerId.toString()).block();

        verify(feedBroadcaster).publishLike(postId.toString(), authorId, 7);
    }

    @Test
    void like_doesNotBroadcastOnARepeatedLike() {
        when(postRepository.like(postId, likerId)).thenReturn(Mono.just(false));

        useCase.like(postId.toString(), likerId.toString()).block();

        verify(feedBroadcaster, never()).publishLike(any(), any(), anyLong());
    }
}
