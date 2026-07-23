package com.techpulse.techradar.integration;

import org.junit.jupiter.api.Test;

/** Radar top4/top10/search and compare, all fed by the Neo4j-to-Postgres analytics ETL. */
class RadarCompareIntegrationTest extends IntegrationTestSupport {

    @Test
    void radar_top4_top10_search_and_compare() {
        String admin = adminToken();
        seedAndEtl(admin);
        String token = registerAndLogin("u11@test.vn");

        web.get().uri("/api/v1/radar/top4").header("Authorization", bearer(token))
                .exchange().expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data[0].industry").isEqualTo("Python")
                .jsonPath("$.data[0].job_count").isEqualTo(1);

        web.get().uri("/api/v1/radar/top10").header("Authorization", bearer(token))
                .exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.data[0].keyword").isEqualTo("Python");

        web.get().uri("/api/v1/radar/search?keywords=Python&months=24").header("Authorization", bearer(token))
                .exchange().expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data").isArray()
                .jsonPath("$.data[0].keywords.Python").exists();

        web.get().uri("/api/v1/compare/search?keywords=Python&months=24").header("Authorization", bearer(token))
                .exchange().expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data[0].keyword").isEqualTo("Python")
                .jsonPath("$.data[0].monthly").isArray();
    }
}
