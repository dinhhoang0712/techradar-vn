package com.techpulse.techradar.features.notification.application;

import com.techpulse.techradar.features.auth.ports.EmailSender;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlertDeliveryDispatcherTest {

    @Mock
    private NotificationService notificationService;
    @Mock
    private EmailSender emailSender;

    private AlertDeliveryDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        dispatcher = new AlertDeliveryDispatcher(notificationService, emailSender);
    }

    @Test
    void dispatch_savesInAppNotification_whenNotifyInAppTrue() {
        UUID userId = UUID.randomUUID();
        when(notificationService.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(dispatcher.dispatch(userId, "TREND_ALERT", "title", "body", "/radar",
                true, false, "dev@example.com")).verifyComplete();

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationService).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(userId);
        assertThat(captor.getValue().getType()).isEqualTo("TREND_ALERT");
        assertThat(captor.getValue().getTitle()).isEqualTo("title");
        assertThat(captor.getValue().getBody()).isEqualTo("body");
        assertThat(captor.getValue().getLink()).isEqualTo("/radar");
        assertThat(captor.getValue().isRead()).isFalse();
        verify(emailSender, never()).sendNotification(anyString(), anyString(), anyString());
    }

    @Test
    void dispatch_skipsInAppNotification_whenNotifyInAppFalse() {
        StepVerifier.create(dispatcher.dispatch(UUID.randomUUID(), "TREND_ALERT", "title", "body", "/radar",
                false, false, "dev@example.com")).verifyComplete();

        verify(notificationService, never()).save(any());
    }

    @Test
    void dispatch_sendsEmail_whenNotifyEmailTrueAndEmailPresent() {
        when(emailSender.sendNotification("dev@example.com", "title", "body")).thenReturn(Mono.empty());

        StepVerifier.create(dispatcher.dispatch(UUID.randomUUID(), "TREND_ALERT", "title", "body", "/radar",
                false, true, "dev@example.com")).verifyComplete();

        verify(emailSender).sendNotification("dev@example.com", "title", "body");
    }

    @Test
    void dispatch_skipsEmail_whenNotifyEmailTrueButEmailBlank() {
        StepVerifier.create(dispatcher.dispatch(UUID.randomUUID(), "TREND_ALERT", "title", "body", "/radar",
                false, true, "  ")).verifyComplete();

        verify(emailSender, never()).sendNotification(anyString(), anyString(), anyString());
    }

    @Test
    void dispatch_skipsEmail_whenNotifyEmailTrueButEmailNull() {
        StepVerifier.create(dispatcher.dispatch(UUID.randomUUID(), "TREND_ALERT", "title", "body", "/radar",
                false, true, null)).verifyComplete();

        verify(emailSender, never()).sendNotification(anyString(), anyString(), anyString());
    }

    @Test
    void dispatch_deliversBothChannels_whenBothEnabled() {
        when(notificationService.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(emailSender.sendNotification("dev@example.com", "title", "body")).thenReturn(Mono.empty());

        StepVerifier.create(dispatcher.dispatch(UUID.randomUUID(), "TREND_ALERT", "title", "body", "/radar",
                true, true, "dev@example.com")).verifyComplete();

        verify(notificationService).save(any());
        verify(emailSender).sendNotification("dev@example.com", "title", "body");
    }

    @Test
    void dispatch_swallowsEmailFailure_andStillCompletes() {
        when(notificationService.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(emailSender.sendNotification("dev@example.com", "title", "body"))
                .thenReturn(Mono.error(new RuntimeException("smtp down")));

        StepVerifier.create(dispatcher.dispatch(UUID.randomUUID(), "TREND_ALERT", "title", "body", "/radar",
                true, true, "dev@example.com")).verifyComplete();
    }
}
