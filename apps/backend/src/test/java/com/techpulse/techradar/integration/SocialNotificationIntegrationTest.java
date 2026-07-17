package com.techpulse.techradar.integration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.http.MediaType;

import java.util.Map;

/**
 * Messaging + social actions (comment/like/follow) each notify the right recipient exactly once,
 * in-process, no Kafka involved.
 */
@EnabledIfEnvironmentVariable(named = "POSTGRES_HOST", matches = ".+")
class SocialNotificationIntegrationTest extends IntegrationTestSupport {

    @Test
    @SuppressWarnings("unchecked")
    void message_notifiesRecipient_withConversationLink() {
        String alice = registerAndLogin("alice-msg@test.vn");
        String bob = registerAndLogin("bob-msg@test.vn");
        String bobId = meId(bob);

        Map<String, Object> conv = web.post().uri("/api/v1/conversations/with/" + bobId)
                .header("Authorization", bearer(alice))
                .exchange().expectStatus().isOk()
                .expectBody(MAP).returnResult().getResponseBody();
        String conversationId = (String) ((Map<String, Object>) conv.get("data")).get("id");

        web.post().uri("/api/v1/conversations/" + conversationId + "/messages")
                .header("Authorization", bearer(alice))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("content", "Chào Bob!"))
                .exchange().expectStatus().isOk();

        web.get().uri("/api/v1/notifications").header("Authorization", bearer(bob))
                .exchange().expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data[0].type").isEqualTo("NEW_MESSAGE")
                .jsonPath("$.data[0].link").isEqualTo("/messages?conversation=" + conversationId)
                .jsonPath("$.data[1]").doesNotExist();
    }

    @Test
    @SuppressWarnings("unchecked")
    void comment_notifiesPostAuthor_butNotOnSelfComment() {
        String author = registerAndLogin("author-cmt@test.vn");
        String commenter = registerAndLogin("commenter-cmt@test.vn");

        Map<String, Object> post = web.post().uri("/api/v1/posts").header("Authorization", bearer(author))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("content", "Hello feed"))
                .exchange().expectStatus().isOk()
                .expectBody(MAP).returnResult().getResponseBody();
        String postId = (String) ((Map<String, Object>) post.get("data")).get("id");

        // commenting on your own post shouldn't notify you
        web.post().uri("/api/v1/posts/" + postId + "/comments").header("Authorization", bearer(author))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("content", "self note"))
                .exchange().expectStatus().isOk();

        web.get().uri("/api/v1/notifications").header("Authorization", bearer(author))
                .exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.data[0]").doesNotExist();

        // someone else commenting does notify the author
        web.post().uri("/api/v1/posts/" + postId + "/comments").header("Authorization", bearer(commenter))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("content", "nice post"))
                .exchange().expectStatus().isOk();

        web.get().uri("/api/v1/notifications").header("Authorization", bearer(author))
                .exchange().expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data[0].type").isEqualTo("POST_COMMENT")
                .jsonPath("$.data[0].link").isEqualTo("/feed")
                .jsonPath("$.data[1]").doesNotExist();
    }

    @Test
    @SuppressWarnings("unchecked")
    void like_notifiesPostAuthor_onlyOnceAndNotOnSelfLike() {
        String author = registerAndLogin("author-like@test.vn");
        String liker = registerAndLogin("liker-like@test.vn");

        Map<String, Object> post = web.post().uri("/api/v1/posts").header("Authorization", bearer(author))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("content", "Like me"))
                .exchange().expectStatus().isOk()
                .expectBody(MAP).returnResult().getResponseBody();
        String postId = (String) ((Map<String, Object>) post.get("data")).get("id");

        // liking your own post shouldn't notify you
        web.post().uri("/api/v1/posts/" + postId + "/like").header("Authorization", bearer(author))
                .exchange().expectStatus().isOk();

        // liking twice (idempotent insert) must only notify once
        web.post().uri("/api/v1/posts/" + postId + "/like").header("Authorization", bearer(liker))
                .exchange().expectStatus().isOk();
        web.post().uri("/api/v1/posts/" + postId + "/like").header("Authorization", bearer(liker))
                .exchange().expectStatus().isOk();

        web.get().uri("/api/v1/notifications").header("Authorization", bearer(author))
                .exchange().expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data[0].type").isEqualTo("POST_LIKE")
                .jsonPath("$.data[0].link").isEqualTo("/feed")
                .jsonPath("$.data[1]").doesNotExist();
    }

    @Test
    void follow_notifiesFollowee_onlyOnce() {
        String follower = registerAndLogin("follower-flw@test.vn");
        String followee = registerAndLogin("followee-flw@test.vn");
        String followerId = meId(follower);
        String followeeId = meId(followee);

        web.post().uri("/api/v1/users/" + followeeId + "/follow").header("Authorization", bearer(follower))
                .exchange().expectStatus().isOk();
        // following again (idempotent insert) must not duplicate the notification
        web.post().uri("/api/v1/users/" + followeeId + "/follow").header("Authorization", bearer(follower))
                .exchange().expectStatus().isOk();

        web.get().uri("/api/v1/notifications").header("Authorization", bearer(followee))
                .exchange().expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data[0].type").isEqualTo("NEW_FOLLOWER")
                .jsonPath("$.data[0].link").isEqualTo("/users/" + followerId)
                .jsonPath("$.data[1]").doesNotExist();
    }
}
