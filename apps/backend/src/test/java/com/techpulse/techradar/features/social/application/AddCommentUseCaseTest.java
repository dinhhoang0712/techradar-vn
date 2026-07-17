package com.techpulse.techradar.features.social.application;

import com.techpulse.techradar.features.auth.domain.User;
import com.techpulse.techradar.features.auth.ports.UserRepository;
import com.techpulse.techradar.features.notification.application.NotificationService;
import com.techpulse.techradar.features.notification.domain.Notification;
import com.techpulse.techradar.features.social.ports.CommentRepository;
import com.techpulse.techradar.features.social.ports.PostRepository;
import com.techpulse.techradar.shared.exception.AppException;
import com.techpulse.techradar.shared.exception.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
    @Mock
    private MentionNotifier mentionNotifier;

    private AddCommentUseCase useCase;

    private final UUID postId = UUID.randomUUID();
    private final UUID authorId = UUID.randomUUID();
    private final UUID commenterId = UUID.randomUUID();
    private final UUID parentCommentId = UUID.randomUUID();
    private final UUID parentAuthorId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        useCase = new AddCommentUseCase(commentRepository, postRepository, notificationService, userRepository, mentionNotifier);
        lenient().when(commentRepository.insert(any(), any(), any(), any(), any(), any())).thenReturn(Mono.empty());
        lenient().when(mentionNotifier.notify(any(), any(), any(), any())).thenReturn(Mono.empty());
        lenient().when(notificationService.save(any())).thenReturn(Mono.just(Notification.builder().build()));
    }

    @Test
    void execute_notifiesPostAuthorWhenCommenterIsSomeoneElse() {
        when(postRepository.findAuthorId(postId)).thenReturn(Mono.just(authorId));
        when(userRepository.findById(commenterId.toString()))
                .thenReturn(Mono.just(User.builder().id(commenterId).fullName("Trần Thị B").build()));

        StepVerifier.create(useCase.execute(postId.toString(), commenterId.toString(), "Great post!", null, null))
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

        StepVerifier.create(useCase.execute(postId.toString(), commenterId.toString(), "My own post, nice", null, null))
                .expectNextCount(1)
                .verifyComplete();

        verify(notificationService, never()).save(any());
    }

    @Test
    void execute_stillSucceedsWhenNotificationLookupFails() {
        when(postRepository.findAuthorId(postId)).thenReturn(Mono.error(new RuntimeException("boom")));

        StepVerifier.create(useCase.execute(postId.toString(), commenterId.toString(), "Resilient comment", null, null))
                .expectNextCount(1)
                .verifyComplete();
    }

    @Test
    void execute_rejectsEmptyContent() {
        StepVerifier.create(useCase.execute(postId.toString(), commenterId.toString(), "   ", null, null))
                .expectError()
                .verify();
    }

    @Test
    void execute_truncatesLongContentInNotificationPreview() {
        String longContent = "x".repeat(200);
        when(postRepository.findAuthorId(postId)).thenReturn(Mono.just(authorId));
        when(userRepository.findById(commenterId.toString()))
                .thenReturn(Mono.just(User.builder().id(commenterId).fullName("Trần Thị B").build()));

        useCase.execute(postId.toString(), commenterId.toString(), longContent, null, null).block();

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationService).save(captor.capture());
        assertThat(captor.getValue().getBody()).hasSize(141).endsWith("…");
    }

    @Test
    void execute_notifiesBothPostAuthorAndParentAuthorOnAReply() {
        when(postRepository.findAuthorId(postId)).thenReturn(Mono.just(authorId));
        when(commentRepository.findParentInfo(parentCommentId))
                .thenReturn(Mono.just(new CommentRepository.ParentInfo(postId, parentAuthorId, null)));
        when(userRepository.findById(commenterId.toString()))
                .thenReturn(Mono.just(User.builder().id(commenterId).fullName("Trần Thị B").build()));

        StepVerifier.create(useCase.execute(postId.toString(), commenterId.toString(), "Reply!", parentCommentId.toString(), null))
                .expectNextCount(1)
                .verifyComplete();

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationService, times(2)).save(captor.capture());
        List<Notification> saved = captor.getAllValues();
        assertThat(saved).extracting(Notification::getUserId).containsExactlyInAnyOrder(authorId, parentAuthorId);
        assertThat(saved).extracting(Notification::getType).containsExactlyInAnyOrder("POST_COMMENT", "COMMENT_REPLY");
    }

    @Test
    void execute_doesNotDoubleNotifyPostAuthorWhoAlsoWroteTheParentComment() {
        // Replying to a top-level comment written by the post's own author: POST_COMMENT already
        // covers them, so COMMENT_REPLY must not fire a second notification for the same person.
        when(postRepository.findAuthorId(postId)).thenReturn(Mono.just(authorId));
        when(commentRepository.findParentInfo(parentCommentId))
                .thenReturn(Mono.just(new CommentRepository.ParentInfo(postId, authorId, null)));
        when(userRepository.findById(commenterId.toString()))
                .thenReturn(Mono.just(User.builder().id(commenterId).fullName("Trần Thị B").build()));

        useCase.execute(postId.toString(), commenterId.toString(), "Reply!", parentCommentId.toString(), null).block();

        verify(notificationService, times(1)).save(any());
    }

    @Test
    void execute_doesNotNotifyParentAuthorWhoIsRepliedToByThemselves() {
        when(postRepository.findAuthorId(postId)).thenReturn(Mono.just(authorId));
        when(commentRepository.findParentInfo(parentCommentId))
                .thenReturn(Mono.just(new CommentRepository.ParentInfo(postId, commenterId, null)));
        when(userRepository.findById(commenterId.toString()))
                .thenReturn(Mono.just(User.builder().id(commenterId).fullName("Trần Thị B").build()));

        useCase.execute(postId.toString(), commenterId.toString(), "Reply to my own comment", parentCommentId.toString(), null).block();

        // Only the (someone-else) post author notification fires; no self-notification for the reply.
        verify(notificationService, times(1)).save(any());
    }

    @Test
    void execute_rejectsReplyToANonexistentParent() {
        when(commentRepository.findParentInfo(parentCommentId)).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute(postId.toString(), commenterId.toString(), "Reply!", parentCommentId.toString(), null))
                .expectErrorSatisfies(e -> assertThat(e).isInstanceOf(NotFoundException.class))
                .verify();
    }

    @Test
    void execute_rejectsReplyBelongingToADifferentPost() {
        UUID otherPostId = UUID.randomUUID();
        when(commentRepository.findParentInfo(parentCommentId))
                .thenReturn(Mono.just(new CommentRepository.ParentInfo(otherPostId, parentAuthorId, null)));

        StepVerifier.create(useCase.execute(postId.toString(), commenterId.toString(), "Reply!", parentCommentId.toString(), null))
                .expectErrorSatisfies(e -> {
                    assertThat(e).isInstanceOf(AppException.class);
                    assertThat(((AppException) e).getErrorCode()).isEqualTo("INVALID_PARENT");
                })
                .verify();
    }

    @Test
    void execute_rejectsReplyingToAReply() {
        when(commentRepository.findParentInfo(parentCommentId))
                .thenReturn(Mono.just(new CommentRepository.ParentInfo(postId, parentAuthorId, UUID.randomUUID())));

        StepVerifier.create(useCase.execute(postId.toString(), commenterId.toString(), "Reply!", parentCommentId.toString(), null))
                .expectErrorSatisfies(e -> {
                    assertThat(e).isInstanceOf(AppException.class);
                    assertThat(((AppException) e).getErrorCode()).isEqualTo("INVALID_PARENT");
                })
                .verify();
    }

    @Test
    void execute_forwardsMentionedUserIdsToMentionNotifier() {
        when(postRepository.findAuthorId(postId)).thenReturn(Mono.just(commenterId));
        List<String> mentioned = List.of(UUID.randomUUID().toString());

        useCase.execute(postId.toString(), commenterId.toString(), "Hi @someone", null, mentioned).block();

        verify(mentionNotifier).notify(commenterId, mentioned, "bình luận", "/feed");
    }

    @Test
    void execute_rejectsTooManyMentionsBeforeInsertingTheComment() {
        List<String> tooMany = IntStream.range(0, 11).mapToObj(i -> UUID.randomUUID().toString()).toList();

        StepVerifier.create(useCase.execute(postId.toString(), commenterId.toString(), "Hi everyone", null, tooMany))
                .expectErrorSatisfies(e -> assertThat(((AppException) e).getErrorCode()).isEqualTo("INVALID_MENTIONS"))
                .verify();

        verify(commentRepository, never()).insert(any(), any(), any(), any(), any(), any());
        verify(mentionNotifier, never()).notify(any(), any(), any(), any());
    }
}
