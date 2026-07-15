package com.techpulse.techradar.features.social.application;

import com.techpulse.techradar.features.auth.domain.User;
import com.techpulse.techradar.features.auth.ports.UserRepository;
import com.techpulse.techradar.features.notification.application.NotificationService;
import com.techpulse.techradar.features.notification.domain.Notification;
import com.techpulse.techradar.features.social.ports.CommentRepository;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AddCommentUseCaseTest {

    @Mock
    private CommentRepository commentRepository;
    @Mock
    private PostRepository postRepository;
    @Mock
    private NotificationService notificationService;
    @Mock
    private UserRepository userRepository;

    private AddCommentUseCase useCase;

    private final UUID postId = UUID.randomUUID();
    private final UUID authorId = UUID.randomUUID();
    private final UUID commenterId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        useCase = new AddCommentUseCase(commentRepository, postRepository, notificationService, userRepository);
        lenient().when(commentRepository.insert(any(), any(), any(), any(), any())).thenReturn(Mono.empty());
    }

    @Test
    void execute_notifiesPostAuthorWhenCommenterIsSomeoneElse() {
        when(postRepository.findAuthorId(postId)).thenReturn(Mono.just(authorId));
        when(userRepository.findById(commenterId.toString()))
                .thenReturn(Mono.just(User.builder().id(commenterId).fullName("Trần Thị B").build()));
        when(notificationService.save(any())).thenReturn(Mono.just(Notification.builder().build()));

        StepVerifier.create(useCase.execute(postId.toString(), commenterId.toString(), "Great post!"))
                .expectNextCount(1)
                .verifyComplete();

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationService).save(captor.capture());
        Notification notification = captor.getValue();
        assertThat(notification.getUserId()).isEqualTo(authorId);
        assertThat(notification.getType()).isEqualTo("POST_COMMENT");
        assertThat(notification.getTitle()).contains("Trần Thị B");
        assertThat(notification.getBody()).isEqualTo("Great post!");
    }

    @Test
    void execute_doesNotNotifyWhenCommenterIsTheAuthor() {
        when(postRepository.findAuthorId(postId)).thenReturn(Mono.just(commenterId));

        StepVerifier.create(useCase.execute(postId.toString(), commenterId.toString(), "My own post, nice"))
                .expectNextCount(1)
                .verifyComplete();

        verify(notificationService, never()).save(any());
    }

    @Test
    void execute_stillSucceedsWhenNotificationLookupFails() {
        when(postRepository.findAuthorId(postId)).thenReturn(Mono.error(new RuntimeException("boom")));

        StepVerifier.create(useCase.execute(postId.toString(), commenterId.toString(), "Resilient comment"))
                .expectNextCount(1)
                .verifyComplete();
    }

    @Test
    void execute_rejectsEmptyContent() {
        StepVerifier.create(useCase.execute(postId.toString(), commenterId.toString(), "   "))
                .expectError()
                .verify();
    }

    @Test
    void execute_truncatesLongContentInNotificationPreview() {
        String longContent = "x".repeat(200);
        when(postRepository.findAuthorId(postId)).thenReturn(Mono.just(authorId));
        when(userRepository.findById(commenterId.toString()))
                .thenReturn(Mono.just(User.builder().id(commenterId).fullName("Trần Thị B").build()));
        when(notificationService.save(any())).thenReturn(Mono.just(Notification.builder().build()));

        useCase.execute(postId.toString(), commenterId.toString(), longContent).block();

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationService).save(captor.capture());
        assertThat(captor.getValue().getBody()).hasSize(141).endsWith("…");
    }
}
