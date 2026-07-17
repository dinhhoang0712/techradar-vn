package com.techpulse.techradar.integration;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Auth flows: register/login/refresh/logout envelope shape, and forgot/reset password. */
class AuthIntegrationTest extends IntegrationTestSupport {

    @Test
    void register_returnsBareTokens_snakeCase() {
        Map<String, Object> body = registerUser("u1@test.vn");
        assertThat(body).containsKey("access_token");
        assertThat(body).containsKey("refresh_token");
        assertThat(body).doesNotContainKey("data");
        assertThat(body.get("role")).isEqualTo("user");
    }

    @Test
    void login_then_me_areBare() {
        String token = registerAndLogin("u2@test.vn");
        web.get().uri("/api/v1/auth/me").header("Authorization", bearer(token))
                .exchange().expectStatus().isOk()
                .expectBody()
                .jsonPath("$.email").isEqualTo("u2@test.vn")
                .jsonPath("$.role").isEqualTo("user")
                .jsonPath("$.data").doesNotExist();
    }

    @Test
    void refresh_returnsBareToken_and_logoutOk() {
        Map<String, Object> reg = registerUser("u7@test.vn");
        String refresh = (String) reg.get("refresh_token");

        web.post().uri("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("refresh_token", refresh))
                .exchange().expectStatus().isOk()
                .expectBody()
                .jsonPath("$.access_token").exists()
                .jsonPath("$.data").doesNotExist();

        web.post().uri("/api/v1/auth/logout").contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("refresh_token", refresh))
                .exchange().expectStatus().isOk();
    }

    @Test
    void protectedEndpoint_requiresAuth() {
        web.get().uri("/api/v1/user/profile").exchange().expectStatus().isUnauthorized();
    }

    @Test
    void invalidLogin_is401_withMessage() {
        registerUser("u8@test.vn");
        web.post().uri("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("email", "u8@test.vn", "password", "wrong-pass"))
                .exchange().expectStatus().isUnauthorized()
                .expectBody().jsonPath("$.message").exists();
    }

    @Test
    void password_reset_flow() {
        registerUser("reset@test.vn"); // password Passw0rd!

        web.post().uri("/api/v1/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("email", "reset@test.vn"))
                .exchange().expectStatus().isOk();

        String tokenStr = db.sql(
                "SELECT token FROM password_reset WHERE user_id = " +
                "(SELECT id FROM users WHERE email = 'reset@test.vn') ORDER BY created_at DESC LIMIT 1")
                .map((row, meta) -> row.get("token", UUID.class).toString())
                .one().block();
        assertThat(tokenStr).isNotBlank();

        web.post().uri("/api/v1/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("token", tokenStr, "new_password", "NewPass1!"))
                .exchange().expectStatus().isOk();

        // old password rejected, new password works
        web.post().uri("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("email", "reset@test.vn", "password", "Passw0rd!"))
                .exchange().expectStatus().isUnauthorized();
        assertThat(login("reset@test.vn", "NewPass1!")).isNotBlank();

        // invalid token -> 400
        web.post().uri("/api/v1/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("token", "not-a-uuid", "new_password", "NewPass1!"))
                .exchange().expectStatus().isBadRequest();
    }
}
