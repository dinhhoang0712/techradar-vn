package com.techpulse.techradar.features.social.application;

import com.techpulse.techradar.features.auth.domain.User;
import com.techpulse.techradar.features.auth.ports.UserRepository;
import com.techpulse.techradar.features.notification.application.NotificationService;
import com.techpulse.techradar.features.notification.domain.Notification;
import com.techpulse.techradar.features.social.ports.PostRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ToggleLikeUseCaseTest {

    @Mock
    private PostRepository postRepository;
    @Mock
    private NotificationService notificationService;
    @Mock
    private UserRepository userRepository;

    private ToggleLikeUseCase useCase;

    private final UUID postId = UUID.randomUUID();
    private final UUID authorId = UUID.randomUUID();
    private final UUID likerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        useCase = new ToggleLikeUseCase(postRepository, notificationService, userRepository);
    }

    @Test
    void like_notifiesAuthorOnANewLikeBySomeoneElse() {
        when(postRepository.like(postId, likerId)).thenReturn(Mono.just(true));
        when(postRepository.findAuthorId(postId)).thenReturn(Mono.just(authorId));
        when(userRepository.findById(likerId.toString()))
                .thenReturn(Mono.just(User.builder().id(likerId).fullName("Lê Văn C").build()));
        when(notificationService.save(any())).thenReturn(Mono.just(Notification.builder().build()));

        StepVerifier.create(useCase.like(postId.toString(), likerId.toString())).verifyComplete();

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationService).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(authorId);
        assertThat(captor.getValue().getType()).isEqualTo("POST_LIKE");
        assertThat(captor.getValue().getTitle()).contains("Lê Văn C");
    }

    @Test
    void like_doesNotNotifyOnARepeatedLike() {
        when(postRepository.like(postId, likerId)).thenReturn(Mono.just(false));

        StepVerifier.create(useCase.like(postId.toString(), likerId.toString())).verifyComplete();

        verify(notificationService, never()).save(any());
    }

    @Test
    void like_doesNotNotifyWhenLikingYourOwnPost() {
        when(postRepository.like(postId, likerId)).thenReturn(Mono.just(true));
        when(postRepository.findAuthorId(postId)).thenReturn(Mono.just(likerId));

        StepVerifier.create(useCase.like(postId.toString(), likerId.toString())).verifyComplete();

        verify(notificationService, never()).save(any());
    }

    @Test
    void like_stillSucceedsWhenNotificationLookupFails() {
        when(postRepository.like(postId, likerId)).thenReturn(Mono.just(true));
        when(postRepository.findAuthorId(postId)).thenReturn(Mono.error(new RuntimeException("boom")));

        StepVerifier.create(useCase.like(postId.toString(), likerId.toString())).verifyComplete();
    }
}
