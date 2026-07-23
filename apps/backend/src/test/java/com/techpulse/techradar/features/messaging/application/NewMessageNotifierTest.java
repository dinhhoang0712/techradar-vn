package com.techpulse.techradar.features.messaging.application;

import com.techpulse.techradar.features.auth.domain.User;
import com.techpulse.techradar.features.auth.ports.UserRepository;
import com.techpulse.techradar.features.notification.application.NotificationService;
import com.techpulse.techradar.features.notification.domain.Notification;
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

@ExtendWith(MockitoExtension.class)
class NewMessageNotifierTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private NotificationService notificationService;

    private NewMessageNotifier notifier;

    private final UUID conversationId = UUID.randomUUID();
    private final UUID recipientId = UUID.randomUUID();
    private final UUID senderId = UUID.randomUUID();

    @Test
    void notify_savesNotificationWithSenderNameAndConversationLink() {
        notifier = new NewMessageNotifier(userRepository, notificationService);
        User sender = User.builder().id(senderId).fullName("Trần Thị B").build();
        when(userRepository.findById(senderId.toString())).thenReturn(Mono.just(sender));
        when(notificationService.save(any(Notification.class))).thenReturn(Mono.just(Notification.builder().build()));

        StepVerifier.create(notifier.notify(conversationId, recipientId, senderId, "Chào bạn!"))
                .verifyComplete();

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationService).save(captor.capture());
        Notification saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(recipientId);
        assertThat(saved.getType()).isEqualTo("NEW_MESSAGE");
        assertThat(saved.getTitle()).isEqualTo("Tin nhắn mới từ Trần Thị B");
        assertThat(saved.getBody()).isEqualTo("Chào bạn!");
        assertThat(saved.getLink()).isEqualTo("/messages?conversation=" + conversationId);
        assertThat(saved.isRead()).isFalse();
    }

    @Test
    void notify_truncatesContentLongerThan140CharsWithEllipsis() {
        notifier = new NewMessageNotifier(userRepository, notificationService);
        User sender = User.builder().id(senderId).fullName("Sender").build();
        when(userRepository.findById(senderId.toString())).thenReturn(Mono.just(sender));
        when(notificationService.save(any(Notification.class))).thenReturn(Mono.just(Notification.builder().build()));
        String longContent = "a".repeat(200);

        StepVerifier.create(notifier.notify(conversationId, recipientId, senderId, longContent))
                .verifyComplete();

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationService).save(captor.capture());
        String body = captor.getValue().getBody();
        assertThat(body).hasSize(141);
        assertThat(body).endsWith("…");
        assertThat(body).startsWith("a".repeat(140));
    }

    @Test
    void notify_doesNotTruncateContentAtExactly140Chars() {
        notifier = new NewMessageNotifier(userRepository, notificationService);
        User sender = User.builder().id(senderId).fullName("Sender").build();
        when(userRepository.findById(senderId.toString())).thenReturn(Mono.just(sender));
        when(notificationService.save(any(Notification.class))).thenReturn(Mono.just(Notification.builder().build()));
        String exactContent = "a".repeat(140);

        StepVerifier.create(notifier.notify(conversationId, recipientId, senderId, exactContent))
                .verifyComplete();

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationService).save(captor.capture());
        assertThat(captor.getValue().getBody()).isEqualTo(exactContent);
    }

    @Test
    void notify_completesWithoutSavingWhenSenderCannotBeFound() {
        notifier = new NewMessageNotifier(userRepository, notificationService);
        when(userRepository.findById(senderId.toString())).thenReturn(Mono.empty());

        StepVerifier.create(notifier.notify(conversationId, recipientId, senderId, "hi"))
                .verifyComplete();

        verify(notificationService, never()).save(any(Notification.class));
    }
}
