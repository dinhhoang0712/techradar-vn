package com.techpulse.techradar.features.social.application;

import com.techpulse.techradar.features.auth.domain.User;
import com.techpulse.techradar.features.auth.ports.UserRepository;
import com.techpulse.techradar.features.notification.application.NotificationService;
import com.techpulse.techradar.features.notification.domain.Notification;
import com.techpulse.techradar.features.social.ports.FollowRepository;
import com.techpulse.techradar.shared.exception.AppException;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ToggleFollowUseCaseTest {

    @Mock
    private FollowRepository followRepository;
    @Mock
    private NotificationService notificationService;
    @Mock
    private UserRepository userRepository;

    private ToggleFollowUseCase useCase;

    private final UUID followeeId = UUID.randomUUID();
    private final UUID followerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        useCase = new ToggleFollowUseCase(followRepository, notificationService, userRepository);
    }

    @Test
    void follow_notifiesFolloweeOnANewFollow() {
        when(followRepository.follow(followerId, followeeId)).thenReturn(Mono.just(true));
        when(userRepository.findById(followerId.toString()))
                .thenReturn(Mono.just(User.builder().id(followerId).fullName("Phạm Văn D").build()));
        when(notificationService.save(any())).thenReturn(Mono.just(Notification.builder().build()));

        StepVerifier.create(useCase.follow(followerId.toString(), followeeId.toString())).verifyComplete();

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationService).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(followeeId);
        assertThat(captor.getValue().getType()).isEqualTo("NEW_FOLLOWER");
        assertThat(captor.getValue().getTitle()).contains("Phạm Văn D");
    }

    @Test
    void follow_doesNotNotifyWhenAlreadyFollowing() {
        when(followRepository.follow(followerId, followeeId)).thenReturn(Mono.just(false));

        StepVerifier.create(useCase.follow(followerId.toString(), followeeId.toString())).verifyComplete();

        verify(notificationService, never()).save(any());
    }

    @Test
    void follow_rejectsFollowingYourself_withoutTouchingTheRepository() {
        StepVerifier.create(useCase.follow(followerId.toString(), followerId.toString()))
                .expectErrorSatisfies(e -> {
                    assertThat(e).isInstanceOf(AppException.class);
                    assertThat(((AppException) e).getErrorCode()).isEqualTo("INVALID_FOLLOW");
                })
                .verify();

        verifyNoInteractions(followRepository, notificationService);
    }

    @Test
    void follow_stillSucceedsWhenNotificationLookupFails() {
        when(followRepository.follow(followerId, followeeId)).thenReturn(Mono.just(true));
        when(userRepository.findById(followerId.toString())).thenReturn(Mono.error(new RuntimeException("boom")));

        StepVerifier.create(useCase.follow(followerId.toString(), followeeId.toString())).verifyComplete();
    }
}
