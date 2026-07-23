package com.techpulse.techradar.integration;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.Map;

/** Graph explore/road-analysis/filter against a real Neo4j instance. */
class GraphIntegrationTest extends IntegrationTestSupport {

    @Test
    void graph_explore_road_filter() {
        seedGraph();
        String token = registerAndLogin("u10@test.vn");

        web.get().uri("/api/v1/graph/explore?keywords=Python&depth=2").header("Authorization", bearer(token))
                .exchange().expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.found").isEqualTo(true)
                .jsonPath("$.data.nodes").isArray()
                .jsonPath("$.data.edges").isArray();

        web.get().uri("/api/v1/graph/road_analysis?from=Python&to=Django").header("Authorization", bearer(token))
                .exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.data.found").isEqualTo(true);

        web.post().uri("/api/v1/graph/filter").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("node_types", List.of("Technology")))
                .exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.data").isArray();
    }
}
