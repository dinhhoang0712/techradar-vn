package com.techpulse.techradar.features.notification.adapters.input;

import com.techpulse.techradar.features.notification.application.NotificationService;
import com.techpulse.techradar.features.notification.domain.Notification;
import com.techpulse.techradar.shared.dto.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextImpl;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.util.context.Context;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {

    @Mock
    private NotificationService notificationService;

    private NotificationController controller;

    @BeforeEach
    void setUp() {
        controller = new NotificationController(notificationService);
    }

    private static Context withUser(String userId) {
        var auth = new UsernamePasswordAuthenticationToken(userId, null, List.of());
        SecurityContext securityContext = new SecurityContextImpl(auth);
        return ReactiveSecurityContextHolder.withSecurityContext(Mono.just(securityContext));
    }

    private static Notification notification(String type) {
        return Notification.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .type(type)
                .title("title")
                .body("body")
                .link("/link")
                .read(false)
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    void list_returnsCurrentUsersNotifications_mappedToView() {
        when(notificationService.list("user-1", 50, 0)).thenReturn(Flux.just(notification("TREND_ALERT")));

        StepVerifier.create(controller.list(50, 0).contextWrite(withUser("user-1")))
                .assertNext(response -> {
                    ApiResponse<List<NotificationView>> body = response.getBody();
                    assertThat(body.getData()).hasSize(1);
                    assertThat(body.getData().get(0).getType()).isEqualTo("TREND_ALERT");
                })
                .verifyComplete();
    }

    @Test
    void list_clampsLimitToOneHundred() {
        when(notificationService.list("user-1", 100, 0)).thenReturn(Flux.empty());

        StepVerifier.create(controller.list(999, 0).contextWrite(withUser("user-1"))).expectNextCount(1).verifyComplete();

        verify(notificationService).list("user-1", 100, 0);
    }

    @Test
    void list_clampsLimitToAtLeastOne() {
        when(notificationService.list("user-1", 1, 0)).thenReturn(Flux.empty());

        StepVerifier.create(controller.list(0, 0).contextWrite(withUser("user-1"))).expectNextCount(1).verifyComplete();

        verify(notificationService).list("user-1", 1, 0);
    }

    @Test
    void list_clampsNegativeOffsetToZero() {
        when(notificationService.list("user-1", 50, 0)).thenReturn(Flux.empty());

        StepVerifier.create(controller.list(50, -5).contextWrite(withUser("user-1"))).expectNextCount(1).verifyComplete();

        verify(notificationService).list("user-1", 50, 0);
    }

    @Test
    void markRead_delegatesToServiceWithCurrentUserId() {
        UUID id = UUID.randomUUID();
        when(notificationService.markRead(id.toString(), "user-1")).thenReturn(Mono.empty());

        StepVerifier.create(controller.markRead(id.toString()).contextWrite(withUser("user-1")))
                .assertNext(response -> assertThat(response.getBody().isSuccess()).isTrue())
                .verifyComplete();

        verify(notificationService).markRead(id.toString(), "user-1");
    }

    @Test
    void markAllRead_delegatesToServiceWithCurrentUserId() {
        when(notificationService.markAllRead("user-1")).thenReturn(Mono.empty());

        StepVerifier.create(controller.markAllRead().contextWrite(withUser("user-1")))
                .assertNext(response -> assertThat(response.getBody().isSuccess()).isTrue())
                .verifyComplete();

        verify(notificationService).markAllRead("user-1");
    }

    @Test
    void stream_forwardsNotificationsForCurrentUserAsServerSentEvents() {
        Notification n = notification("JOB_MATCH");
        when(notificationService.streamFor("user-1")).thenReturn(Flux.just(n));

        StepVerifier.create(controller.stream().contextWrite(withUser("user-1")).take(1).timeout(Duration.ofSeconds(5)))
                .assertNext(sse -> {
                    assertThat(sse.event()).isEqualTo("notification");
                    assertThat(sse.data().getType()).isEqualTo("JOB_MATCH");
                })
                .verifyComplete();
    }
}
