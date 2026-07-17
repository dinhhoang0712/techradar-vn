package com.techpulse.techradar.integration;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Feed v2: hashtags, explore scope, images, company tag, mentions, threaded replies. */
class FeedIntegrationTest extends IntegrationTestSupport {

    @Test
    @SuppressWarnings("unchecked")
    void hashtag_isParsedDedupedFilterableAndTrending() {
        String token = registerAndLogin("hashtag@test.vn");
        String tag = "uniquetag" + UUID.randomUUID().toString().replace("-", "");

        web.post().uri("/api/v1/posts").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("content", "Học #" + tag + " và #" + tag + " hôm nay"))
                .exchange().expectStatus().isOk();

        // stored lowercase, deduped (appears twice in content, once in the derived array)
        web.get().uri("/api/v1/feed").header("Authorization", bearer(token))
                .exchange().expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data[0].hashtags[0]").isEqualTo(tag)
                .jsonPath("$.data[0].hashtags[1]").doesNotExist();

        // feed hashtag filter (@> on the GIN-indexed array)
        web.get().uri("/api/v1/feed?hashtag=" + tag).header("Authorization", bearer(token))
                .exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.data[0]").exists();
        web.get().uri("/api/v1/feed?hashtag=nope-" + tag).header("Authorization", bearer(token))
                .exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.data[0]").doesNotExist();

        // trending aggregation (unnest + GROUP BY over the last 7 days)
        web.get().uri("/api/v1/hashtags/trending?limit=50").header("Authorization", bearer(token))
                .exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.data[?(@.tag=='" + tag + "')]").exists();
    }

    @Test
    @SuppressWarnings("unchecked")
    void feedScope_followingExcludesNonFollowedAuthor_exploreIncludesEveryone() {
        String author = registerAndLogin("scope-author@test.vn");
        String viewer = registerAndLogin("scope-viewer@test.vn");

        Map<String, Object> post = web.post().uri("/api/v1/posts").header("Authorization", bearer(author))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("content", "Explore me please"))
                .exchange().expectStatus().isOk()
                .expectBody(MAP).returnResult().getResponseBody();
        String postId = (String) ((Map<String, Object>) post.get("data")).get("id");

        // viewer doesn't follow author -> absent from the default ("following") feed
        web.get().uri("/api/v1/feed").header("Authorization", bearer(viewer))
                .exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.data[?(@.id=='" + postId + "')]").doesNotExist();

        // ...but present in the "explore" (every public post) feed
        web.get().uri("/api/v1/feed?scope=explore").header("Authorization", bearer(viewer))
                .exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.data[?(@.id=='" + postId + "')]").exists();
    }

    @Test
    @SuppressWarnings("unchecked")
    void postImage_uploadServesPubliclyAndAppearsInFeed() {
        String token = registerAndLogin("post-image@test.vn");
        // 1x1 PNG (same fixture as avatar_upload_and_serve_public)
        String png = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==";

        web.post().uri("/api/v1/posts").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                        "content", "Check out this pic",
                        "images", List.of(Map.of("content_type", "image/png", "data_base64", png))))
                .exchange().expectStatus().isOk();

        Map<String, Object> feedRes = web.get().uri("/api/v1/feed").header("Authorization", bearer(token))
                .exchange().expectStatus().isOk()
                .expectBody(MAP).returnResult().getResponseBody();
        List<Map<String, Object>> data = (List<Map<String, Object>>) feedRes.get("data");
        List<String> imageUrls = (List<String>) data.get(0).get("image_urls");
        assertThat(imageUrls).hasSize(1);

        // public serve (no Authorization header), same as avatar images
        byte[] body = web.get().uri(imageUrls.get(0))
                .exchange().expectStatus().isOk()
                .expectHeader().contentType(MediaType.IMAGE_PNG)
                .expectBody(byte[].class).returnResult().getResponseBody();
        assertThat(body).isNotEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void postCompanyTag_snapshotsNameAndLocation_andRejectsUnknownId() {
        String token = registerAndLogin("company-tag@test.vn");
        String companyId = "test-company-" + UUID.randomUUID();
        seedCompany(companyId, "Acme Test Corp", "Đà Nẵng");

        Map<String, Object> post = web.post().uri("/api/v1/posts").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("content", "Working at Acme!", "tagged_company_id", companyId))
                .exchange().expectStatus().isOk()
                .expectBody(MAP).returnResult().getResponseBody();
        assertThat(post.get("data")).isNotNull();

        web.get().uri("/api/v1/feed").header("Authorization", bearer(token))
                .exchange().expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data[0].tagged_company.id").isEqualTo(companyId)
                .jsonPath("$.data[0].tagged_company.name").isEqualTo("Acme Test Corp")
                .jsonPath("$.data[0].tagged_company.location").isEqualTo("Đà Nẵng");

        web.post().uri("/api/v1/posts").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("content", "Tagging a ghost company",
                        "tagged_company_id", "does-not-exist-" + UUID.randomUUID()))
                .exchange().expectStatus().isBadRequest()
                .expectBody().jsonPath("$.errorCode").isEqualTo("INVALID_COMPANY");
    }

    @Test
    @SuppressWarnings("unchecked")
    void mention_notifiesTaggedUser_andIgnoresGarbageIdsWithoutFailingThePost() {
        String author = registerAndLogin("mention-author@test.vn");
        String mentioned = registerAndLogin("mention-target@test.vn");
        String mentionedId = meId(mentioned);
        String authorId = meId(author);

        // self-mention + an unparseable id must both be silently dropped, not fail the post
        web.post().uri("/api/v1/posts").header("Authorization", bearer(author))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                        "content", "Hi @Mention Target",
                        "mentioned_user_ids", List.of(mentionedId, authorId, "not-a-real-uuid")))
                .exchange().expectStatus().isOk();

        web.get().uri("/api/v1/notifications").header("Authorization", bearer(mentioned))
                .exchange().expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data[0].type").isEqualTo("POST_MENTION")
                .jsonPath("$.data[1]").doesNotExist();

        // author mentioned themselves -> no self-notification
        web.get().uri("/api/v1/notifications").header("Authorization", bearer(author))
                .exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.data[0]").doesNotExist();
    }

    @Test
    @SuppressWarnings("unchecked")
    void reply_isFlatWithParentId_notifiesParentAuthor_andRejectsReplyToAReply() {
        String author = registerAndLogin("thread-author@test.vn");
        String commenter = registerAndLogin("thread-commenter@test.vn");
        String replier = registerAndLogin("thread-replier@test.vn");

        Map<String, Object> post = web.post().uri("/api/v1/posts").header("Authorization", bearer(author))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("content", "Thread me"))
                .exchange().expectStatus().isOk()
                .expectBody(MAP).returnResult().getResponseBody();
        String postId = (String) ((Map<String, Object>) post.get("data")).get("id");

        Map<String, Object> comment = web.post().uri("/api/v1/posts/" + postId + "/comments")
                .header("Authorization", bearer(commenter))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("content", "top-level comment"))
                .exchange().expectStatus().isOk()
                .expectBody(MAP).returnResult().getResponseBody();
        String commentId = (String) ((Map<String, Object>) comment.get("data")).get("id");

        Map<String, Object> reply = web.post().uri("/api/v1/posts/" + postId + "/comments")
                .header("Authorization", bearer(replier))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("content", "a reply", "parent_id", commentId))
                .exchange().expectStatus().isOk()
                .expectBody(MAP).returnResult().getResponseBody();
        String replyId = (String) ((Map<String, Object>) reply.get("data")).get("id");

        // flat list (no total count, no nested JSON) — each item just carries parent_id
        web.get().uri("/api/v1/posts/" + postId + "/comments")
                .exchange().expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data[0].parent_id").doesNotExist()
                .jsonPath("$.data[1].parent_id").isEqualTo(commentId);

        // the top-level commenter gets COMMENT_REPLY (not a second POST_COMMENT — that's the
        // post author's notification, dedup logic in AddCommentUseCase.notifyAll)
        web.get().uri("/api/v1/notifications").header("Authorization", bearer(commenter))
                .exchange().expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data[0].type").isEqualTo("COMMENT_REPLY")
                .jsonPath("$.data[1]").doesNotExist();

        // replying to a reply is rejected (only 1 level of nesting is supported)
        web.post().uri("/api/v1/posts/" + postId + "/comments").header("Authorization", bearer(author))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("content", "reply to a reply", "parent_id", replyId))
                .exchange().expectStatus().isBadRequest()
                .expectBody().jsonPath("$.errorCode").isEqualTo("INVALID_PARENT");
    }
}
