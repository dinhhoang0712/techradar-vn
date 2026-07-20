package com.techpulse.techradar.features.roadmap.application;

import com.techpulse.techradar.features.kafka.KafkaTopicConstants;
import com.techpulse.techradar.features.kafka.producer.KafkaProducerService;
import com.techpulse.techradar.features.notification.domain.TrendSubscriber;
import com.techpulse.techradar.features.notification.event.RoadmapAlertEvent;
import com.techpulse.techradar.features.notification.ports.NotificationRepository;
import com.techpulse.techradar.features.roadmap.domain.RoadmapResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Weekly scan (see {@code RoadmapAlertScheduler}): for every user with profile technologies,
 * recompute their roadmap — which also refreshes {@code cache:roadmap:<userId>} for their next
 * visit — and, if their #1 recommended next skill is growing at least
 * {@code app.notifications.trend-threshold}%, publish a {@code roadmap.alerts} event so they hear
 * about it proactively instead of only on page visit.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoadmapAlertService {

    /** Bounds how many per-user roadmap computations (each triggering 2 LLM calls) run at once. */
    private static final int CONCURRENCY = 4;

    private final NotificationRepository notificationRepository;
    private final GetCareerRoadmapUseCase getCareerRoadmapUseCase;
    private final KafkaProducerService kafkaProducer;

    @Value("${app.notifications.trend-threshold:30}")
    private double trendThreshold;

    /** @return number of alerts published. */
    public Mono<Long> runOnce() {
        return notificationRepository.findRoadmapCandidates()
                .flatMap(this::evaluate, CONCURRENCY)
                .filter(Boolean::booleanValue)
                .count()
                .doOnSuccess(n -> log.info("Roadmap alert scan done: {} alert(s) published", n));
    }

    private Mono<Boolean> evaluate(TrendSubscriber candidate) {
        return getCareerRoadmapUseCase.execute(candidate.userId().toString())
                .map(roadmap -> topHotSkill(roadmap)
                        .map(skill -> publish(candidate, skill))
                        .orElse(false))
                .onErrorResume(e -> {
                    log.warn("Roadmap alert scan: failed for user {}", candidate.userId(), e);
                    return Mono.just(false);
                });
    }

    private Optional<Map<String, Object>> topHotSkill(RoadmapResult roadmap) {
        List<Map<String, Object>> nextSkills = roadmap.nextSkills();
        if (nextSkills == null || nextSkills.isEmpty()) {
            return Optional.empty();
        }
        Map<String, Object> top = nextSkills.get(0);
        return !techName(top).isBlank() && growthRate(top) >= trendThreshold
                ? Optional.of(top)
                : Optional.empty();
    }

    private boolean publish(TrendSubscriber candidate, Map<String, Object> skill) {
        RoadmapAlertEvent event = new RoadmapAlertEvent(
                candidate.userId().toString(), candidate.email(),
                candidate.notifyInapp(), candidate.notifyEmail(),
                techName(skill), growthRate(skill));
        try {
            kafkaProducer.send(KafkaTopicConstants.ROADMAP_ALERTS, event);
            return true;
        } catch (Exception e) {
            log.warn("Could not publish roadmap alert for user {} (Kafka unavailable?)", candidate.userId(), e);
            return false;
        }
    }

    private static String techName(Map<String, Object> skill) {
        Object v = skill.get("tech_name");
        return v == null ? "" : String.valueOf(v);
    }

    private static double growthRate(Map<String, Object> skill) {
        Object v = skill.get("growth_rate");
        return v instanceof Number n ? n.doubleValue() : 0.0;
    }
}
