package com.techpulse.techradar.features.social.application;

import com.techpulse.techradar.features.auth.domain.User;
import com.techpulse.techradar.features.auth.ports.UserRepository;
import com.techpulse.techradar.features.notification.application.NotificationService;
import com.techpulse.techradar.features.notification.domain.Notification;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MentionNotifierTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private NotificationService notificationService;

    private MentionNotifier notifier;

    private final UUID actorId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        notifier = new MentionNotifier(userRepository, notificationService);
    }

    @Test
    void tooMany_isFalseForNullOrUpToTheCap_trueBeyondIt() {
        assertThat(MentionNotifier.tooMany(null)).isFalse();
        assertThat(MentionNotifier.tooMany(List.of())).isFalse();
        assertThat(MentionNotifier.tooMany(idList(MentionNotifier.MAX_MENTIONS))).isFalse();
        assertThat(MentionNotifier.tooMany(idList(MentionNotifier.MAX_MENTIONS + 1))).isTrue();
    }

    @Test
    void notify_noOpsForNullOrEmptyList() {
        StepVerifier.create(notifier.notify(actorId, null, "bài viết", "/feed")).verifyComplete();
        StepVerifier.create(notifier.notify(actorId, List.of(), "bài viết", "/feed")).verifyComplete();
        verifyNoInteractions(userRepository, notificationService);
    }

    @Test
    void notify_noOpsWithNoInteractionsWhenOnlyMentionIsSelf() {
        StepVerifier.create(notifier.notify(actorId, List.of(actorId.toString()), "bài viết", "/feed")).verifyComplete();
        verifyNoInteractions(userRepository, notificationService);
    }

    @Test
    void notify_sendsOneNotificationWithTypeTitleAndLink() {
        UUID targetId = UUID.randomUUID();
        when(userRepository.findById(actorId.toString()))
                .thenReturn(Mono.just(User.builder().id(actorId).fullName("Nguyễn Văn A").build()));
        when(userRepository.findById(targetId.toString())).thenReturn(Mono.just(User.builder().id(targetId).build()));
        when(notificationService.save(any())).thenReturn(Mono.just(Notification.builder().build()));

        StepVerifier.create(notifier.notify(actorId, List.of(targetId.toString()), "bài viết", "/feed")).verifyComplete();

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationService).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(targetId);
        assertThat(captor.getValue().getType()).isEqualTo("POST_MENTION");
        assertThat(captor.getValue().getTitle()).contains("Nguyễn Văn A");
        assertThat(captor.getValue().getLink()).isEqualTo("/feed");
    }

    @Test
    void notify_dedupesRepeatedTargetIds() {
        UUID targetId = UUID.randomUUID();
        when(userRepository.findById(actorId.toString()))
                .thenReturn(Mono.just(User.builder().id(actorId).fullName("Actor").build()));
        when(userRepository.findById(targetId.toString())).thenReturn(Mono.just(User.builder().id(targetId).build()));
        when(notificationService.save(any())).thenReturn(Mono.just(Notification.builder().build()));

        notifier.notify(actorId, List.of(targetId.toString(), targetId.toString()), "bài viết", "/feed").block();

        verify(notificationService, times(1)).save(any());
    }

    @Test
    void notify_removesSelfButStillNotifiesOtherTargets() {
        UUID targetId = UUID.randomUUID();
        when(userRepository.findById(actorId.toString()))
                .thenReturn(Mono.just(User.builder().id(actorId).fullName("Actor").build()));
        when(userRepository.findById(targetId.toString())).thenReturn(Mono.just(User.builder().id(targetId).build()));
        when(notificationService.save(any())).thenReturn(Mono.just(Notification.builder().build()));

        notifier.notify(actorId, List.of(actorId.toString(), targetId.toString()), "bài viết", "/feed").block();

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationService, times(1)).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(targetId);
    }

    @Test
    void notify_skipsAnUnknownOrDeletedTargetIdSilently() {
        UUID knownId = UUID.randomUUID();
        UUID unknownId = UUID.randomUUID();
        when(userRepository.findById(actorId.toString()))
                .thenReturn(Mono.just(User.builder().id(actorId).fullName("Actor").build()));
        when(userRepository.findById(knownId.toString())).thenReturn(Mono.just(User.builder().id(knownId).build()));
        when(userRepository.findById(unknownId.toString())).thenReturn(Mono.empty());
        when(notificationService.save(any())).thenReturn(Mono.just(Notification.builder().build()));

        StepVerifier.create(notifier.notify(actorId, List.of(knownId.toString(), unknownId.toString()), "bài viết", "/feed"))
                .verifyComplete();

        verify(notificationService, times(1)).save(any());
    }

    @Test
    void notify_skipsAMalformedTargetIdWithoutFailingTheOtherTargets() {
        UUID knownId = UUID.randomUUID();
        when(userRepository.findById(actorId.toString()))
                .thenReturn(Mono.just(User.builder().id(actorId).fullName("Actor").build()));
        when(userRepository.findById(knownId.toString())).thenReturn(Mono.just(User.builder().id(knownId).build()));
        when(notificationService.save(any())).thenReturn(Mono.just(Notification.builder().build()));

        StepVerifier.create(notifier.notify(actorId, List.of(knownId.toString(), "not-a-uuid"), "bài viết", "/feed"))
                .verifyComplete();

        verify(notificationService, times(1)).save(any());
    }

    @Test
    void notify_isBestEffort_swallowsNotificationSaveFailure() {
        UUID targetId = UUID.randomUUID();
        when(userRepository.findById(actorId.toString()))
                .thenReturn(Mono.just(User.builder().id(actorId).fullName("Actor").build()));
        when(userRepository.findById(targetId.toString())).thenReturn(Mono.just(User.builder().id(targetId).build()));
        when(notificationService.save(any())).thenReturn(Mono.error(new RuntimeException("boom")));

        StepVerifier.create(notifier.notify(actorId, List.of(targetId.toString()), "bài viết", "/feed"))
                .verifyComplete();
    }

    @Test
    void notify_isBestEffort_swallowsActorLookupFailure() {
        when(userRepository.findById(actorId.toString())).thenReturn(Mono.error(new RuntimeException("boom")));

        StepVerifier.create(notifier.notify(actorId, List.of(UUID.randomUUID().toString()), "bài viết", "/feed"))
                .verifyComplete();

        verify(notificationService, never()).save(any());
    }

    private static List<String> idList(int size) {
        return IntStream.range(0, size).mapToObj(i -> UUID.randomUUID().toString()).toList();
    }
}
