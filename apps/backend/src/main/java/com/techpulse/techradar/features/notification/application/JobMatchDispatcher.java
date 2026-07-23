package com.techpulse.techradar.features.notification.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techpulse.techradar.features.kafka.KafkaTopicConstants;
import com.techpulse.techradar.features.notification.domain.JobMatchSubscriber;
import com.techpulse.techradar.features.notification.event.JobMatchEvent;
import com.techpulse.techradar.features.notification.ports.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

/**
 * Consumes {@code job.match.alerts} domain events (one per brand-new job posting) and fans them
 * out to users whose profile {@code technologies} OR {@code target_skills} (the roadmap's
 * "learning next" recommendations, see {@code GetCareerRoadmapUseCase}) overlap the job's, across
 * channels (in-app + email) — with different copy for each case. Mirrors
 * {@link TrendAlertDispatcher}: the producer (the Kafka-to-Neo4j job writer) stays decoupled from
 * delivery.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JobMatchDispatcher {

    private final NotificationRepository repository;
    private final AlertDeliveryDispatcher alertDeliveryDispatcher;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = KafkaTopicConstants.JOB_MATCH_ALERTS, groupId = "notification-dispatcher")
    public void onJobMatch(ConsumerRecord<String, String> record) {
        try {
            JobMatchEvent event = objectMapper.readValue(record.value(), JobMatchEvent.class);
            if (event.getTechnologies() == null || event.getTechnologies().isEmpty()) {
                return;
            }

            long delivered = repository.findJobMatchSubscribers(event.getTechnologies())
                    .flatMap(sub -> dispatch(sub, event).thenReturn(1))
                    .count()
                    .blockOptional()
                    .orElse(0L);
            log.info("Job match alert '{}' dispatched to {} subscriber(s)", event.getJobTitle(), delivered);
        } catch (Exception e) {
            log.error("Failed to dispatch job match alert: {}", record.value(), e);
        }
    }

    private Mono<Void> dispatch(JobMatchSubscriber sub, JobMatchEvent event) {
        String companySuffix = event.getCompanyName() != null && !event.getCompanyName().isBlank()
                ? " tại " + event.getCompanyName() : "";
        String type;
        String title;
        String body;
        if (sub.matchesCurrentSkills()) {
            type = "JOB_MATCH";
            title = "Việc làm mới phù hợp: " + event.getJobTitle();
            body = String.format("%s%s vừa được đăng, khớp với kỹ năng của bạn. Xem chi tiết trên trang Sự nghiệp.",
                    event.getJobTitle(), companySuffix);
        } else {
            type = "JOB_MATCH_LEARNING";
            title = "Việc làm cho kỹ năng bạn đang học: " + event.getJobTitle();
            body = String.format(
                    "%s%s vừa được đăng, khớp với kỹ năng bạn đang học theo lộ trình. Xem chi tiết trên trang Sự nghiệp.",
                    event.getJobTitle(), companySuffix);
        }
        return alertDeliveryDispatcher.dispatch(sub.userId(), type, title, body, careerLink(event),
                sub.notifyInapp(), sub.notifyEmail(), sub.email());
    }

    /**
     * Deep-links to the roadmap page with the job's technologies so the frontend can highlight
     * the matching "next skill to learn" card/path (see TechRecommendationCards' tech_path
     * visual) instead of dropping the user on an unfiltered /career page.
     */
    private String careerLink(JobMatchEvent event) {
        String highlight = event.getTechnologies().stream()
                .map(t -> URLEncoder.encode(t, StandardCharsets.UTF_8))
                .collect(Collectors.joining(","));
        return "/career?highlight=" + highlight;
    }
}
