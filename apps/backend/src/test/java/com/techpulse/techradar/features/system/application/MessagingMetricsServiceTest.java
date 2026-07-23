package com.techpulse.techradar.features.system.application;

import com.techpulse.techradar.features.messaging.ports.MessagingStatsRepository;
import com.techpulse.techradar.features.notification.ports.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessagingMetricsServiceTest {

    @Mock
    private MessagingStatsRepository messagingStatsRepository;
    @Mock
    private NotificationRepository notificationRepository;

    private MessagingMetricsService service;

    @BeforeEach
    void setUp() {
        service = new MessagingMetricsService(messagingStatsRepository, notificationRepository);
    }

    @Test
    void messagingVolume_combinesConversationMessageAndNotificationCounts() {
        when(messagingStatsRepository.countConversations()).thenReturn(Mono.just(15L));
        when(messagingStatsRepository.countMessages()).thenReturn(Mono.just(200L));
        when(messagingStatsRepository.countMessagesSince(any(LocalDateTime.class))).thenReturn(Mono.just(12L));
        when(notificationRepository.countGroupedByType()).thenReturn(Flux.just(
                new NotificationRepository.TypeCount("NEW_MESSAGE", 40L)));

        StepVerifier.create(service.messagingVolume())
                .assertNext(stats -> {
                    assertThat(stats.getTotalConversations()).isEqualTo(15L);
                    assertThat(stats.getTotalMessages()).isEqualTo(200L);
                    assertThat(stats.getMessagesToday()).isEqualTo(12L);
                    assertThat(stats.getNotificationsByType()).hasSize(1);
                    assertThat(stats.getNotificationsByType().get(0).getType()).isEqualTo("NEW_MESSAGE");
                    assertThat(stats.getNotificationsByType().get(0).getCount()).isEqualTo(40L);
                })
                .verifyComplete();
    }
}
