package com.techpulse.techradar.integration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Chat session lifecycle: health, session create/list/delete, messages, ownership. */
@EnabledIfEnvironmentVariable(named = "POSTGRES_HOST", matches = ".+")
class ChatIntegrationTest extends IntegrationTestSupport {

    @Test
    void chat_health_session_message_history() {
        String token = registerAndLogin("u9@test.vn");

        web.get().uri("/api/v1/chat").header("Authorization", bearer(token))
                .exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.data.status").isEqualTo("ok");

        String sid = createChatSession(token);
        assertThat(sid).isNotBlank();

        web.get().uri("/api/v1/chat/sessions").header("Authorization", bearer(token))
                .exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.data").isArray();

        web.post().uri("/api/v1/chat/session/" + sid + "/messages").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("query", "hello"))
                .exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.data.answer").isEqualTo("Hi from RAG");

        web.get().uri("/api/v1/chat/session/" + sid + "/messages").header("Authorization", bearer(token))
                .exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.data").isArray();
    }

    @Test
    void chat_session_ownership_isEnforced() {
        String owner = registerAndLogin("owner@test.vn");
        String other = registerAndLogin("other@test.vn");
        String sid = createChatSession(owner);

        web.get().uri("/api/v1/chat/session/" + sid + "/messages").header("Authorization", bearer(other))
                .exchange().expectStatus().isForbidden();
    }

    @Test
    void chat_session_delete_and_ownership() {
        String owner = registerAndLogin("del-owner@test.vn");
        String other = registerAndLogin("del-other@test.vn");
        String sid = createChatSession(owner);

        // another user cannot delete someone else's session
        web.delete().uri("/api/v1/chat/session/" + sid).header("Authorization", bearer(other))
                .exchange().expectStatus().isForbidden();

        // owner deletes -> ok, and it disappears from their session list
        web.delete().uri("/api/v1/chat/session/" + sid).header("Authorization", bearer(owner))
                .exchange().expectStatus().isOk();

        web.get().uri("/api/v1/chat/sessions").header("Authorization", bearer(owner))
                .exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.data[0]").doesNotExist();
    }
}
