package com.techpulse.techradar.integration;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** User profile (users.full_name + user_profile text[]) and avatar upload/serve. */
class UserProfileIntegrationTest extends IntegrationTestSupport {

    @Test
    void profile_update_persistsTechnologiesArray() {
        String token = registerAndLogin("u3@test.vn");

        web.put().uri("/api/v1/user/profile").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("full_name", "Jane Dev", "job_role", "Backend Engineer",
                        "bio", "hello", "location", "Hanoi",
                        "technologies", List.of("Java", "Spring")))
                .exchange().expectStatus().isOk();

        web.get().uri("/api/v1/user/profile").header("Authorization", bearer(token))
                .exchange().expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.full_name").isEqualTo("Jane Dev")
                .jsonPath("$.data.job_role").isEqualTo("Backend Engineer")
                .jsonPath("$.data.location").isEqualTo("Hanoi")
                .jsonPath("$.data.technologies[0]").isEqualTo("Java")
                .jsonPath("$.data.technologies[1]").isEqualTo("Spring");
    }

    @Test
    @SuppressWarnings("unchecked")
    void avatar_upload_and_serve_public() {
        String token = registerAndLogin("avatar@test.vn");
        // 1x1 PNG
        String png = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==";

        Map<String, Object> res = web.post().uri("/api/v1/user/avatar").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("content_type", "image/png", "data_base64", png))
                .exchange().expectStatus().isOk()
                .expectBody(MAP).returnResult().getResponseBody();
        String url = (String) ((Map<String, Object>) res.get("data")).get("avatar_url");
        assertThat(url).contains("/user/avatar/");

        // public serve (no Authorization header)
        byte[] body = web.get().uri(url)
                .exchange().expectStatus().isOk()
                .expectHeader().contentType(MediaType.IMAGE_PNG)
                .expectBody(byte[].class).returnResult().getResponseBody();
        assertThat(body).isNotEmpty();

        web.get().uri("/api/v1/user/profile").header("Authorization", bearer(token))
                .exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.data.avatar_url").isEqualTo(url);
    }
}
