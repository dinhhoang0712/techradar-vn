package com.techpulse.techradar.integration;

import com.techpulse.techradar.features.notification.domain.JobMatchSubscriber;
import com.techpulse.techradar.features.notification.ports.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.Map;
import java.util.UUID;

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

        List<JobMatchSubscriber> matches = notificationRepository
                .findJobMatchSubscribers(List.of("Kotlin", "Rust"))
                .collectList().block();
        assertThat(matches)
                .filteredOn(s -> s.userId().toString().equals(userId))
                .hasSize(1)
                .allMatch(JobMatchSubscriber::matchesCurrentSkills);

        List<JobMatchSubscriber> noMatches = notificationRepository
                .findJobMatchSubscribers(List.of("COBOL"))
                .collectList().block();
        assertThat(noMatches).extracting(s -> s.userId().toString()).doesNotContain(userId);
    }

    @Test
    void jobMatchSubscribers_matchByTargetSkillOverlapIsFlaggedAsNotCurrentSkills() {
        String token = registerAndLogin("jobmatch-learner@test.vn");
        String userId = meId(token);
        web.put().uri("/api/v1/user/profile").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("full_name", "Job Match Learner", "technologies", List.of("Java")))
                .exchange().expectStatus().isOk();
        // target_skills is system-managed (persisted by GetCareerRoadmapUseCase), not exposed via
        // the profile edit API — set it directly to simulate a roadmap recompute having run.
        db.sql("UPDATE user_profile SET target_skills = :skills WHERE user_id = :user_id")
                .bind("user_id", UUID.fromString(userId))
                .bind("skills", new String[] { "Kubernetes" })
                .fetch().rowsUpdated().block();

        List<JobMatchSubscriber> matches = notificationRepository
                .findJobMatchSubscribers(List.of("Kubernetes"))
                .collectList().block();
        assertThat(matches)
                .filteredOn(s -> s.userId().toString().equals(userId))
                .hasSize(1)
                .noneMatch(JobMatchSubscriber::matchesCurrentSkills);
    }
}
