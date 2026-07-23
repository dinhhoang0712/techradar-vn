package com.techpulse.techradar.integration;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.Map;

/** Clustering endpoints proxy through to the mocked Python ClusteringServicePort. */
class ClusteringIntegrationTest extends IntegrationTestSupport {

    @Test
    void clustering_list_detail_tech_batch() {
        String token = registerAndLogin("u5@test.vn");

        web.get().uri("/api/v1/clustering/clusters?is_coherent=true").header("Authorization", bearer(token))
                .exchange().expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data[0].cluster_id").isEqualTo(0)
                .jsonPath("$.data[0].label_en").isEqualTo("Python Backend");

        web.get().uri("/api/v1/clustering/clusters/0").header("Authorization", bearer(token))
                .exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.data.members[0]").isEqualTo("Django");

        web.get().uri("/api/v1/clustering/tech/Python/cluster").header("Authorization", bearer(token))
                .exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.data.found").isEqualTo(true);

        web.post().uri("/api/v1/clustering/predict/batch").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("tech_names", List.of("Python", "Django")))
                .exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.data.n_found").isEqualTo(1);
    }
}
