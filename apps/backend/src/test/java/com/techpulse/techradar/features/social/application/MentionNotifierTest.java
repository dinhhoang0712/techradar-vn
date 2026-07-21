package com.techpulse.techradar.features.social.application;

import com.techpulse.techradar.features.auth.domain.User;
import com.techpulse.techradar.features.auth.ports.UserRepository;
import com.techpulse.techradar.features.notification.application.ActivityNotifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
    private ActivityNotifier activityNotifier;

    private MentionNotifier notifier;

    private final UUID actorId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        notifier = new MentionNotifier(userRepository, activityNotifier);
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
        verifyNoInteractions(userRepository, activityNotifier);
    }

    @Test
    void notify_noOpsWithNoInteractionsWhenOnlyMentionIsSelf() {
        StepVerifier.create(notifier.notify(actorId, List.of(actorId.toString()), "bài viết", "/feed")).verifyComplete();
        verifyNoInteractions(userRepository, activityNotifier);
    }

    @Test
    void notify_sendsOneNotificationWithTypeTitleAndLink() {
        UUID targetId = UUID.randomUUID();
        when(userRepository.findById(targetId.toString())).thenReturn(Mono.just(User.builder().id(targetId).build()));
        when(activityNotifier.notify(actorId, targetId, "POST_MENTION", "đã nhắc đến bạn trong một bài viết", "/feed"))
                .thenReturn(Mono.empty());

        StepVerifier.create(notifier.notify(actorId, List.of(targetId.toString()), "bài viết", "/feed")).verifyComplete();

        verify(activityNotifier).notify(actorId, targetId, "POST_MENTION", "đã nhắc đến bạn trong một bài viết", "/feed");
    }

    @Test
    void notify_dedupesRepeatedTargetIds() {
        UUID targetId = UUID.randomUUID();
        when(userRepository.findById(targetId.toString())).thenReturn(Mono.just(User.builder().id(targetId).build()));
        when(activityNotifier.notify(actorId, targetId, "POST_MENTION", "đã nhắc đến bạn trong một bài viết", "/feed"))
                .thenReturn(Mono.empty());

        notifier.notify(actorId, List.of(targetId.toString(), targetId.toString()), "bài viết", "/feed").block();

        verify(activityNotifier, times(1)).notify(any(), any(), any(), any(), any());
    }

    @Test
    void notify_removesSelfButStillNotifiesOtherTargets() {
        UUID targetId = UUID.randomUUID();
        when(userRepository.findById(targetId.toString())).thenReturn(Mono.just(User.builder().id(targetId).build()));
        when(activityNotifier.notify(actorId, targetId, "POST_MENTION", "đã nhắc đến bạn trong một bài viết", "/feed"))
                .thenReturn(Mono.empty());

        notifier.notify(actorId, List.of(actorId.toString(), targetId.toString()), "bài viết", "/feed").block();

        verify(activityNotifier, times(1)).notify(actorId, targetId, "POST_MENTION", "đã nhắc đến bạn trong một bài viết", "/feed");
    }

    @Test
    void notify_skipsAnUnknownOrDeletedTargetIdSilently() {
        UUID knownId = UUID.randomUUID();
        UUID unknownId = UUID.randomUUID();
        when(userRepository.findById(knownId.toString())).thenReturn(Mono.just(User.builder().id(knownId).build()));
        when(userRepository.findById(unknownId.toString())).thenReturn(Mono.empty());
        when(activityNotifier.notify(actorId, knownId, "POST_MENTION", "đã nhắc đến bạn trong một bài viết", "/feed"))
                .thenReturn(Mono.empty());

        StepVerifier.create(notifier.notify(actorId, List.of(knownId.toString(), unknownId.toString()), "bài viết", "/feed"))
                .verifyComplete();

        verify(activityNotifier, times(1)).notify(any(), any(), any(), any(), any());
    }

    @Test
    void notify_skipsAMalformedTargetIdWithoutFailingTheOtherTargets() {
        UUID knownId = UUID.randomUUID();
        when(userRepository.findById(knownId.toString())).thenReturn(Mono.just(User.builder().id(knownId).build()));
        when(activityNotifier.notify(actorId, knownId, "POST_MENTION", "đã nhắc đến bạn trong một bài viết", "/feed"))
                .thenReturn(Mono.empty());

        StepVerifier.create(notifier.notify(actorId, List.of(knownId.toString(), "not-a-uuid"), "bài viết", "/feed"))
                .verifyComplete();

        verify(activityNotifier, times(1)).notify(any(), any(), any(), any(), any());
    }

    @Test
    void notify_isBestEffort_swallowsNotificationSaveFailure() {
        UUID targetId = UUID.randomUUID();
        when(userRepository.findById(targetId.toString())).thenReturn(Mono.just(User.builder().id(targetId).build()));
        when(activityNotifier.notify(actorId, targetId, "POST_MENTION", "đã nhắc đến bạn trong một bài viết", "/feed"))
                .thenReturn(Mono.error(new RuntimeException("boom")));

        StepVerifier.create(notifier.notify(actorId, List.of(targetId.toString()), "bài viết", "/feed"))
                .verifyComplete();
    }

    @Test
    void notify_isBestEffort_swallowsTargetLookupFailure() {
        UUID targetId = UUID.randomUUID();
        when(userRepository.findById(targetId.toString())).thenReturn(Mono.error(new RuntimeException("boom")));

        StepVerifier.create(notifier.notify(actorId, List.of(targetId.toString()), "bài viết", "/feed"))
                .verifyComplete();

        verify(activityNotifier, never()).notify(any(), any(), any(), any(), any());
    }

    private static List<String> idList(int size) {
        return IntStream.range(0, size).mapToObj(i -> UUID.randomUUID().toString()).toList();
    }
}
