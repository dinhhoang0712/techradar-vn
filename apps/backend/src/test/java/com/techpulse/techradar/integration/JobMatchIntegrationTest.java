package com.techpulse.techradar.integration;

import com.techpulse.techradar.features.notification.domain.TrendSubscriber;
import com.techpulse.techradar.features.notification.ports.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Job-match subscriber lookup (SQL correctness; the Kafka round-trip itself needs a live broker
 * and is exercised manually against the docker-compose stack, not here).
 */
class JobMatchIntegrationTest extends IntegrationTestSupport {

    @Autowired
    NotificationRepository notificationRepository;

    @Test
    void jobMatchSubscribers_matchByTechnologyOverlap() {
        String token = registerAndLogin("jobmatch@test.vn");
        String userId = meId(token);
        web.put().uri("/api/v1/user/profile").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("full_name", "Job Match User", "technologies", List.of("Kotlin", "Ktor")))
                .exchange().expectStatus().isOk();

        List<TrendSubscriber> matches = notificationRepository
                .findJobMatchSubscribers(List.of("Kotlin", "Rust"))
                .collectList().block();
        assertThat(matches).extracting(s -> s.userId().toString()).contains(userId);

        List<TrendSubscriber> noMatches = notificationRepository
                .findJobMatchSubscribers(List.of("COBOL"))
                .collectList().block();
        assertThat(noMatches).extracting(s -> s.userId().toString()).doesNotContain(userId);
    }
}
