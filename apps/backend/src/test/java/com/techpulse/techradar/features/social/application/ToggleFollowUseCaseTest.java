package com.techpulse.techradar.features.social.application;

import com.techpulse.techradar.features.notification.application.ActivityNotifier;
import com.techpulse.techradar.features.social.ports.FollowRepository;
import com.techpulse.techradar.shared.exception.AppException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ToggleFollowUseCaseTest {

    @Mock
    private FollowRepository followRepository;
    @Mock
    private ActivityNotifier activityNotifier;

    private ToggleFollowUseCase useCase;

    private final UUID followeeId = UUID.randomUUID();
    private final UUID followerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        useCase = new ToggleFollowUseCase(followRepository, activityNotifier);
    }

    @Test
    void follow_notifiesFolloweeOnANewFollow() {
        when(followRepository.follow(followerId, followeeId)).thenReturn(Mono.just(true));
        when(activityNotifier.notify(followerId, followeeId, "NEW_FOLLOWER", "đã bắt đầu theo dõi bạn",
                "/users/" + followerId)).thenReturn(Mono.empty());

        StepVerifier.create(useCase.follow(followerId.toString(), followeeId.toString())).verifyComplete();

        verify(activityNotifier).notify(followerId, followeeId, "NEW_FOLLOWER", "đã bắt đầu theo dõi bạn",
                "/users/" + followerId);
    }

    @Test
    void follow_doesNotNotifyWhenAlreadyFollowing() {
        when(followRepository.follow(followerId, followeeId)).thenReturn(Mono.just(false));

        StepVerifier.create(useCase.follow(followerId.toString(), followeeId.toString())).verifyComplete();

        verify(activityNotifier, never()).notify(any(), any(), any(), any(), any());
    }

    @Test
    void follow_rejectsFollowingYourself_withoutTouchingTheRepository() {
        StepVerifier.create(useCase.follow(followerId.toString(), followerId.toString()))
                .expectErrorSatisfies(e -> {
                    assertThat(e).isInstanceOf(AppException.class);
                    assertThat(((AppException) e).getErrorCode()).isEqualTo("INVALID_FOLLOW");
                })
                .verify();

        verifyNoInteractions(followRepository, activityNotifier);
    }

    @Test
    void follow_stillSucceedsWhenNotificationLookupFails() {
        when(followRepository.follow(followerId, followeeId)).thenReturn(Mono.just(true));
        when(activityNotifier.notify(followerId, followeeId, "NEW_FOLLOWER", "đã bắt đầu theo dõi bạn",
                "/users/" + followerId)).thenReturn(Mono.error(new RuntimeException("boom")));

        StepVerifier.create(useCase.follow(followerId.toString(), followeeId.toString())).verifyComplete();
    }
}
