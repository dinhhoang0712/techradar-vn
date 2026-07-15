package com.techpulse.techradar.features.notification.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techpulse.techradar.features.auth.ports.EmailSender;
import com.techpulse.techradar.features.kafka.KafkaTopicConstants;
import com.techpulse.techradar.features.notification.domain.Notification;
import com.techpulse.techradar.features.notification.domain.TrendSubscriber;
import com.techpulse.techradar.features.notification.event.JobMatchEvent;
import com.techpulse.techradar.features.notification.ports.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Consumes {@code job.match.alerts} domain events (one per brand-new job posting) and fans them
 * out to users whose profile technologies overlap the job's, across channels (in-app + email).
 * Mirrors {@link TrendAlertDispatcher}: the producer (the Kafka-to-Neo4j job writer) stays
 * decoupled from delivery.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JobMatchDispatcher {

    private final NotificationRepository repository;
    private final NotificationService notificationService;
    private final EmailSender emailSender;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = KafkaTopicConstants.JOB_MATCH_ALERTS, groupId = "notification-dispatcher")
    public void onJobMatch(ConsumerRecord<String, String> record) {
        try {
            JobMatchEvent event = objectMapper.readValue(record.value(), JobMatchEvent.class);
            if (event.getTechnologies() == null || event.getTechnologies().isEmpty()) {
                return;
            }
            String title = "Việc làm mới phù hợp: " + event.getJobTitle();
            String body = String.format(
                    "%s%s vừa được đăng, khớp với kỹ năng của bạn. Xem chi tiết trên trang Sự nghiệp.",
                    event.getJobTitle(),
                    event.getCompanyName() != null && !event.getCompanyName().isBlank()
                            ? " tại " + event.getCompanyName() : "");

            long delivered = repository.findJobMatchSubscribers(event.getTechnologies())
                    .flatMap(sub -> dispatch(sub, title, body).thenReturn(1))
                    .count()
                    .blockOptional()
                    .orElse(0L);
            log.info("Job match alert '{}' dispatched to {} subscriber(s)", event.getJobTitle(), delivered);
        } catch (Exception e) {
            log.error("Failed to dispatch job match alert: {}", record.value(), e);
        }
    }

    private Mono<Void> dispatch(TrendSubscriber sub, String title, String body) {
        Mono<Void> inApp = sub.notifyInapp()
                ? notificationService.save(Notification.builder()
                        .userId(sub.userId())
                        .type("JOB_MATCH")
                        .title(title)
                        .body(body)
                        .link("/career")
                        .read(false)
                        .build()).then()
                : Mono.empty();

        Mono<Void> email = (sub.notifyEmail() && sub.email() != null && !sub.email().isBlank())
                ? emailSender.sendNotification(sub.email(), title, body)
                        .onErrorResume(e -> Mono.empty())
                : Mono.empty();

        return inApp.then(email);
    }
}
