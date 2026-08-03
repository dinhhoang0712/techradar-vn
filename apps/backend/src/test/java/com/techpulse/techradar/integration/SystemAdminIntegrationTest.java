package com.techpulse.techradar.integration;

import com.techpulse.techradar.features.system.ports.ActivityLogRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** /status flags, admin settings/dashboard/users/CMS CRUD, and activity_log tracking. */
class SystemAdminIntegrationTest extends IntegrationTestSupport {

    @Autowired
    ActivityLogRepository activityLog;

    @Test
    void status_isBareFlatFlags() {
        web.get().uri("/api/v1/status").exchange().expectStatus().isOk()
                .expectBody()
                .jsonPath("$.feature_graph").isEqualTo("true")
                .jsonPath("$.maintenance_web").isEqualTo("false")
                .jsonPath("$.data").doesNotExist();
    }

    @Test
    void admin_settings_dashboard_areWrapped() {
        String admin = adminToken();

        web.get().uri("/api/v1/admin/settings").header("Authorization", bearer(admin))
                .exchange().expectStatus().isOk().expectBody().jsonPath("$.data").isArray();

        web.get().uri("/api/v1/admin/dashboard/user-count").header("Authorization", bearer(admin))
                .exchange().expectStatus().isOk().expectBody().jsonPath("$.data").isNumber();

        web.get().uri("/api/v1/admin/dashboard/top-keywords").header("Authorization", bearer(admin))
                .exchange().expectStatus().isOk().expectBody().jsonPath("$.data").isArray();
    }

    @Test
    void admin_settings_update_roundTrips() {
        String admin = adminToken();
        web.put().uri("/api/v1/admin/settings/integration_test_flag").header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("value", "42", "description", "test"))
                .exchange().expectStatus().isOk();

        web.get().uri("/api/v1/admin/settings/integration_test_flag").header("Authorization", bearer(admin))
                .exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.data.value").isEqualTo("42");
    }

    @Test
    void admin_user_crud() {
        String admin = adminToken();

        Map<String, Object> created = web.post().uri("/api/v1/admin/users").header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("email", "crud@test.vn", "password", "Passw0rd!",
                        "full_name", "Crud User", "role", "user", "status", "active"))
                .exchange().expectStatus().isCreated()
                .expectBody(MAP).returnResult().getResponseBody();
        @SuppressWarnings("unchecked")
        String id = (String) ((Map<String, Object>) created.get("data")).get("id");
        assertThat(id).isNotBlank();

        web.put().uri("/api/v1/admin/users/" + id).header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("full_name", "Updated Name"))
                .exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.data.full_name").isEqualTo("Updated Name");

        web.delete().uri("/api/v1/admin/users/" + id).header("Authorization", bearer(admin))
                .exchange().expectStatus().isNoContent();
    }

    @Test
    void nonAdmin_cannotAccessAdmin() {
        String token = registerAndLogin("u4@test.vn");
        web.get().uri("/api/v1/admin/users").header("Authorization", bearer(token))
                .exchange().expectStatus().isForbidden();
    }

    @Test
    void nonAdmin_forbidden_acrossOtherPermissionCodes() {
        // Broader regression net than nonAdmin_cannotAccessAdmin above: catches a permission
        // mapping drifting back to a bare hasRole('ADMIN') (or the wrong code) on any of these,
        // not just user:manage.
        String token = registerAndLogin("perm-check@test.vn");

        web.get().uri("/api/v1/admin/cms").header("Authorization", bearer(token))
                .exchange().expectStatus().isForbidden(); // cms:manage

        web.get().uri("/api/v1/admin/audit-log").header("Authorization", bearer(token))
                .exchange().expectStatus().isForbidden(); // audit:view

        web.post().uri("/api/v1/admin/analytics/rebuild").header("Authorization", bearer(token))
                .exchange().expectStatus().isForbidden(); // analytics:manage

        web.post().uri("/api/v1/admin/cache/companies/evict").header("Authorization", bearer(token))
                .exchange().expectStatus().isForbidden(); // cache:manage
    }

    @Test
    void moderatorRole_canModerateSocial_butCannotManageUsers() {
        // Proves the RBAC design added in V24/V25 for real: "moderator" was added purely as data
        // (V25__moderator_role.sql granting social:moderate + audit:view, nothing else) with zero
        // code change, and it now behaves as a genuinely narrower role - not just a second flavor
        // of "is admin".
        String admin = adminToken();
        web.post().uri("/api/v1/admin/users").header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("email", "mod1@test.vn", "password", "Passw0rd!",
                        "full_name", "Mod One", "role", "moderator", "status", "active"))
                .exchange().expectStatus().isCreated();

        String moderatorToken = login("mod1@test.vn", "Passw0rd!");

        web.get().uri("/api/v1/admin/posts").header("Authorization", bearer(moderatorToken))
                .exchange().expectStatus().isOk(); // social:moderate -> granted

        web.get().uri("/api/v1/admin/users").header("Authorization", bearer(moderatorToken))
                .exchange().expectStatus().isForbidden(); // user:manage -> not granted
    }

    @Test
    @SuppressWarnings("unchecked")
    void cms_crud() {
        String admin = adminToken();

        Map<String, Object> created = web.post().uri("/api/v1/admin/cms").header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("title", "Q1 Report", "type", "Report",
                        "content_date", "2026-05-01", "status", "Pending"))
                .exchange().expectStatus().isCreated()
                .expectBody(MAP).returnResult().getResponseBody();
        String id = (String) ((Map<String, Object>) created.get("data")).get("id");
        assertThat(id).isNotBlank();

        web.get().uri("/api/v1/admin/cms").header("Authorization", bearer(admin))
                .exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.data[0].title").isEqualTo("Q1 Report");

        web.put().uri("/api/v1/admin/cms/" + id).header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("title", "Q1 Report (rev)", "status", "Published"))
                .exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.data.status").isEqualTo("Published");

        web.delete().uri("/api/v1/admin/cms/" + id).header("Authorization", bearer(admin))
                .exchange().expectStatus().isNoContent();
    }

    @Test
    void activityLog_records_and_aggregates() {
        activityLog.recordVisit(null, "/radar/search").block();
        activityLog.recordSearch("KafkaTrackTest").block();

        assertThat(activityLog.countToday("visit").block()).isGreaterThanOrEqualTo(1L);
        assertThat(activityLog.countToday("search").block()).isGreaterThanOrEqualTo(1L);
        assertThat(activityLog.topKeywords(10).collectList().block()).contains("KafkaTrackTest");
        assertThat(activityLog.monthlyVisits().collectList().block()).isNotNull();
    }

    @Test
    void activityLog_recordAiRequest_isCountedTowardsAiRequestsToday() {
        // Regression test for V34: chk_activity_type (V4) originally only allowed 'visit'/'search',
        // so every recordAiRequest() insert violated it and was silently swallowed by
        // AiProxyRequestHandler's best-effort .onErrorResume() — the admin live-metrics "Request AI
        // hôm nay" tile always read 0 no matter how much AI-proxy traffic there was.
        activityLog.recordAiRequest().block();

        assertThat(activityLog.countToday("ai_request").block()).isGreaterThanOrEqualTo(1L);
    }
}
