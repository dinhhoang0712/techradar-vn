package com.techpulse.techradar.features.system.application;

import com.techpulse.techradar.features.messaging.ports.MessagingStatsRepository;
import com.techpulse.techradar.features.notification.ports.NotificationRepository;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Messaging & notification volume metrics for the admin dashboard.
 */
@Component
@RequiredArgsConstructor
public class MessagingMetricsService {

    private final MessagingStatsRepository messagingStatsRepository;
    private final NotificationRepository notificationRepository;

    public Mono<MessagingStats> messagingVolume() {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        return Mono.zip(
                messagingStatsRepository.countConversations(),
                messagingStatsRepository.countMessages(),
                messagingStatsRepository.countMessagesSince(todayStart),
                notificationRepository.countGroupedByType()
                        .map(tc -> new NotificationTypeCount(tc.type(), tc.count()))
                        .collectList()
        ).map(t -> MessagingStats.builder()
                .totalConversations(t.getT1())
                .totalMessages(t.getT2())
                .messagesToday(t.getT3())
                .notificationsByType(t.getT4())
                .build());
    }

    @Value
    @Builder
    public static class MessagingStats {
        long totalConversations;
        long totalMessages;
        long messagesToday;
        List<NotificationTypeCount> notificationsByType;
    }

    @Value
    public static class NotificationTypeCount {
        String type;
        long count;
    }
}
