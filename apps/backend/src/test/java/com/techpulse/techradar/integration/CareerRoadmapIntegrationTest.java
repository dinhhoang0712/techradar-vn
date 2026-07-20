package com.techpulse.techradar.integration;

import com.techpulse.techradar.features.aiproxy.ports.AiProxyPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * {@code GET /career/roadmap} — the unified endpoint combining /recommend, /career and
 * /jobs/matches. ai-rag-core (/recommend, /career) is mocked via {@link AiProxyPort}; job matches
 * run against a real (seeded) Neo4j so the {@code job_matches_needing_it} cross-reference is
 * exercised end-to-end.
 */
@EnabledIfEnvironmentVariable(named = "POSTGRES_HOST", matches = ".+")
class CareerRoadmapIntegrationTest extends IntegrationTestSupport {

    @MockitoBean
    AiProxyPort aiProxyPort;

    @Test
    void roadmap_aggregatesRecommendCareerAndJobMatches() {
        when(aiProxyPort.forward(eq("/recommend"), any(), any())).thenReturn(Mono.just(Map.of(
                "recommendations", List.of(Map.of(
                        "tech_name", "Kubernetes",
                        "reason", "Thường đi cùng Docker trong các tin tuyển dụng",
                        "growth_rate", 42.0)),
                "based_on", List.of("Docker"))));
        when(aiProxyPort.forward(eq("/career"), any(), any())).thenReturn(Mono.just(Map.of(
                "target_role", "Senior Backend Developer",
                "skill_gap", List.of(),
                "roadmap", "...",
                "estimated_months", 6)));

        String token = registerAndLogin("roadmap@test.vn");
        web.put().uri("/api/v1/user/profile").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("full_name", "Roadmap User", "technologies", List.of("Docker")))
                .exchange().expectStatus().isOk();

        try (var session = neo4j.session()) {
            session.run("MATCH (n) DETACH DELETE n");
            session.run(
                    "CREATE (j:Job {title:'Backend Dev'}) " +
                    "CREATE (t1:Technology {name:'Docker'}) " +
                    "CREATE (t2:Technology {name:'Kubernetes'}) " +
                    "CREATE (j)-[:REQUIRES]->(t1) " +
                    "CREATE (j)-[:REQUIRES]->(t2)");
        }

        web.get().uri("/api/v1/career/roadmap").header("Authorization", bearer(token))
                .exchange().expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.has_technologies").isEqualTo(true)
                .jsonPath("$.data.current_technologies[0]").isEqualTo("Docker")
                .jsonPath("$.data.next_skills[0].tech_name").isEqualTo("Kubernetes")
                .jsonPath("$.data.next_skills[0].job_matches_needing_it").isEqualTo(1)
                .jsonPath("$.data.career_path.target_role").isEqualTo("Senior Backend Developer")
                .jsonPath("$.data.job_matches[0].title").isEqualTo("Backend Dev");
    }

    @Test
    void roadmap_returnsEmptySectionsWhenProfileHasNoTechnologies() {
        String token = registerAndLogin("roadmap-empty@test.vn");

        web.get().uri("/api/v1/career/roadmap").header("Authorization", bearer(token))
                .exchange().expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.has_technologies").isEqualTo(false)
                .jsonPath("$.data.next_skills").isEmpty();
    }
}
