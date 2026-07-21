package com.techpulse.techradar.features.notification.application;

import com.techpulse.techradar.features.auth.domain.User;
import com.techpulse.techradar.features.auth.ports.UserRepository;
import com.techpulse.techradar.features.notification.domain.Notification;
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

/**
 * {@link ActivityNotifier} is the shared "someone did something" notifier extracted from
 * {@code ToggleLikeUseCase}, {@code ToggleFollowUseCase} and {@code MentionNotifier}.
 */
@ExtendWith(MockitoExtension.class)
class ActivityNotifierTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private NotificationService notificationService;

    private ActivityNotifier activityNotifier;

    private final UUID actorId = UUID.randomUUID();
    private final UUID targetId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        activityNotifier = new ActivityNotifier(userRepository, notificationService);
    }

    @Test
    void notify_buildsAndSavesATitledNotificationForTheTarget() {
        when(userRepository.findById(actorId.toString()))
                .thenReturn(Mono.just(User.builder().id(actorId).fullName("Nguyễn Văn A").build()));
        when(notificationService.save(any())).thenReturn(Mono.just(Notification.builder().build()));

        StepVerifier.create(activityNotifier.notify(
                        actorId, targetId, "POST_LIKE", "đã thích bài viết của bạn", "/feed"))
                .verifyComplete();

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationService).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(targetId);
        assertThat(captor.getValue().getType()).isEqualTo("POST_LIKE");
        assertThat(captor.getValue().getTitle()).isEqualTo("Nguyễn Văn A đã thích bài viết của bạn");
        assertThat(captor.getValue().getLink()).isEqualTo("/feed");
        assertThat(captor.getValue().isRead()).isFalse();
    }

    @Test
    void notify_noOpsWhenActorIsNotFound() {
        when(userRepository.findById(actorId.toString())).thenReturn(Mono.empty());

        StepVerifier.create(activityNotifier.notify(
                        actorId, targetId, "POST_LIKE", "đã thích bài viết của bạn", "/feed"))
                .verifyComplete();

        verify(notificationService, never()).save(any());
    }

    @Test
    void notify_propagatesActorLookupFailure_lettingTheCallerDecideHowToHandleIt() {
        when(userRepository.findById(actorId.toString())).thenReturn(Mono.error(new RuntimeException("boom")));

        StepVerifier.create(activityNotifier.notify(
                        actorId, targetId, "POST_LIKE", "đã thích bài viết của bạn", "/feed"))
                .verifyError(RuntimeException.class);
    }
}
