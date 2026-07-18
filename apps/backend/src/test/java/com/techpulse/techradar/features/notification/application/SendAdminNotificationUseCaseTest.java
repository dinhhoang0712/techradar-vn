package com.techpulse.techradar.features.notification.application;

import com.techpulse.techradar.features.auth.domain.User;
import com.techpulse.techradar.features.notification.domain.Notification;
import com.techpulse.techradar.features.user.application.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SendAdminNotificationUseCaseTest {

    @Mock
    private NotificationService notificationService;
    @Mock
    private UserService userService;

    private SendAdminNotificationUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new SendAdminNotificationUseCase(notificationService, userService);
    }

    @Test
    void execute_sendsToOneUser_whenTargetUserIdGiven() {
        UUID userId = UUID.randomUUID();
        when(notificationService.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(useCase.execute("Bảo trì", "Hệ thống bảo trì lúc 2h sáng", "/dashboard", userId.toString()))
                .expectNext(1L)
                .verifyComplete();

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationService, times(1)).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(userId);
        assertThat(captor.getValue().getType()).isEqualTo("ADMIN_ANNOUNCEMENT");
        assertThat(captor.getValue().getTitle()).isEqualTo("Bảo trì");
    }

    @Test
    void execute_broadcastsToActiveUsersOnly_whenNoTargetUserId() {
        User active1 = User.builder().id(UUID.randomUUID()).status("active").build();
        User active2 = User.builder().id(UUID.randomUUID()).status("ACTIVE").build();
        User blocked = User.builder().id(UUID.randomUUID()).status("blocked").build();
        when(userService.listUsers()).thenReturn(Flux.just(active1, active2, blocked));
        when(notificationService.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(useCase.execute("Thông báo", "Nội dung", null, null))
                .expectNext(2L)
                .verifyComplete();

        verify(notificationService, times(2)).save(any());
    }

    @Test
    void execute_rejectsBlankTitle() {
        StepVerifier.create(useCase.execute("  ", "body", null, null))
                .expectError(IllegalArgumentException.class)
                .verify();
    }

    @Test
    void execute_rejectsMalformedUserId() {
        StepVerifier.create(useCase.execute("title", "body", null, "not-a-uuid"))
                .expectError(IllegalArgumentException.class)
                .verify();
    }
}
