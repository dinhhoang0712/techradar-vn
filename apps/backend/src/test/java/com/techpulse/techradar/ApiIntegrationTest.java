package com.techpulse.techradar;

import com.techpulse.techradar.features.chat.adapters.input.dto.ChatHealthResponse;
import com.techpulse.techradar.features.chat.adapters.input.dto.ChatResponse;
import com.techpulse.techradar.features.chat.ports.ChatPort;
import com.techpulse.techradar.features.clustering.ports.ClusteringServicePort;
import com.techpulse.techradar.features.system.ports.ActivityLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.neo4j.driver.Driver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Full API integration test against real Postgres (+Flyway/R2DBC) and Neo4j, started externally
 * via the docker CLI and wired through the POSTGRES and NEO4J env vars the app already reads.
 * The Python clustering/RAG ports are mocked. Covers every client-facing endpoint: routing,
 * security, bare-vs-wrapped envelope, snake_case, the profile/user_profile SQL, graph Cypher and
 * the Neo4j-to-Postgres ETL feeding radar/compare.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIfEnvironmentVariable(named = "POSTGRES_HOST", matches = ".+")  // needs external Postgres + Neo4j (see scratchpad/run_it.sh)
class ApiIntegrationTest {

    @LocalServerPort
    int port;

    WebTestClient web;

    @Autowired
    Driver neo4j;

    @Autowired
    ActivityLogRepository activityLog;

    @Autowired
    DatabaseClient db;

    @MockitoBean
    ClusteringServicePort clusteringPort;

    @MockitoBean
    ChatPort chatPort;

    private static final ParameterizedTypeReference<Map<String, Object>> MAP =
            new ParameterizedTypeReference<>() {
            };

    @BeforeEach
    void setup() {
        // Bind to the live server so spring.webflux.base-path (/api/v1) applies.
        web = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .responseTimeout(Duration.ofSeconds(30))
                .build();

        when(clusteringPort.getClusters(any())).thenReturn(Flux.just(
                Map.<String, Object>of("cluster_id", 0, "label_en", "Python Backend", "n_members", 3)));
        when(clusteringPort.getCluster(anyString())).thenReturn(Mono.just(
                Map.<String, Object>of("cluster_id", 0, "members", List.of("Django", "FastAPI"))));
        when(clusteringPort.getTechCluster(anyString())).thenReturn(Mono.just(
                Map.<String, Object>of("tech_name", "Python", "cluster_id", 0, "found", true)));
        when(clusteringPort.predictBatch(any())).thenReturn(Mono.just(
                Map.<String, Object>of("results", List.of(), "n_found", 1, "n_not_found", 0)));

        when(chatPort.getHealth()).thenReturn(Mono.just(new ChatHealthResponse("ok", true, "1.0.0")));
        when(chatPort.chat(any())).thenReturn(Mono.just(
                new ChatResponse("Hi from RAG", null, List.of(), List.of(), List.of(), "q")));
    }

    // ---- helpers --------------------------------------------------------------

    private Map<String, Object> registerUser(String email) {
        return web.post().uri("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("full_name", "Test User", "email", email, "password", "Passw0rd!"))
                .exchange().expectStatus().isCreated()
                .expectBody(MAP).returnResult().getResponseBody();
    }

    private String login(String email, String password) {
        Map<String, Object> body = web.post().uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("email", email, "password", password))
                .exchange().expectStatus().isOk()
                .expectBody(MAP).returnResult().getResponseBody();
        return (String) body.get("access_token");
    }

    private String registerAndLogin(String email) {
        registerUser(email);
        return login(email, "Passw0rd!");
    }

    private String adminToken() {
        return login("admin@techradar.vn", "Admin@12345");
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    @SuppressWarnings("unchecked")
    private String createChatSession(String token) {
        Map<String, Object> body = web.post().uri("/api/v1/chat/session").header("Authorization", bearer(token))
                .exchange().expectStatus().isOk()
                .expectBody(MAP).returnResult().getResponseBody();
        return (String) ((Map<String, Object>) body.get("data")).get("session_id");
    }

    /** Seed a small knowledge graph and run the analytics ETL so radar/compare have data. */
    private void seedAndEtl(String admin) {
        // Start from a clean tech_analytics so assertions don't depend on dev seed sample rows.
        db.sql("DELETE FROM tech_analytics").fetch().rowsUpdated().block();
        seedGraph();
        web.post().uri("/api/v1/admin/analytics/rebuild").header("Authorization", bearer(admin))
                .exchange().expectStatus().isOk();
    }

    private void seedGraph() {
        try (var session = neo4j.session()) {
            session.run("MATCH (n) DETACH DELETE n");
            session.run(
                    "CREATE (t:Technology {name:'Python'}) " +
                    "CREATE (d:Technology {name:'Django'}) " +
                    "CREATE (a:Article {title:'Python rises', published_date:'2026-05-10'}) " +
                    "CREATE (j:Job {title:'Backend Dev'}) " +
                    "CREATE (a)-[:MENTIONS]->(t) " +
                    "CREATE (j)-[:REQUIRES]->(t) " +
                    "CREATE (t)-[:RELATED_TO]->(d)");
        }
    }

    // ==== AUTH + envelope (BARE) ===============================================

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

    // ==== /status (BARE flags) ================================================

    @Test
    void status_isBareFlatFlags() {
        web.get().uri("/api/v1/status").exchange().expectStatus().isOk()
                .expectBody()
                .jsonPath("$.feature_graph").isEqualTo("true")
                .jsonPath("$.maintenance_web").isEqualTo("false")
                .jsonPath("$.data").doesNotExist();
    }

    // ==== profile (users.full_name + user_profile text[]) =====================

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

    // ==== admin ===============================================================

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

    // ==== clustering (proxy) ==================================================

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

    // ==== chat ================================================================

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

    // ==== graph (real Neo4j) ==================================================

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

    // ==== radar + compare (via ETL) ===========================================

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

    // ==== chat session delete + ownership ====================================

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

    // ==== activity tracking (real activity_log SQL) ===========================

    @Test
    void activityLog_records_and_aggregates() {
        activityLog.recordVisit(null, "/radar/search").block();
        activityLog.recordSearch("KafkaTrackTest").block();

        assertThat(activityLog.countToday("visit").block()).isGreaterThanOrEqualTo(1L);
        assertThat(activityLog.countToday("search").block()).isGreaterThanOrEqualTo(1L);
        assertThat(activityLog.topKeywords(10).collectList().block()).contains("KafkaTrackTest");
        assertThat(activityLog.monthlyVisits().collectList().block()).isNotNull();
    }

    // ==== admin CMS CRUD ======================================================

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

    // ==== avatar upload + public serve ========================================

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

    // ==== forgot/reset password ===============================================

    @Test
    void password_reset_flow() {
        registerUser("reset@test.vn"); // password Passw0rd!

        web.post().uri("/api/v1/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("email", "reset@test.vn"))
                .exchange().expectStatus().isOk();

        String tokenStr = db.sql(
                "SELECT token FROM password_reset WHERE user_id = " +
                "(SELECT id FROM users WHERE email = 'reset@test.vn') ORDER BY created_at DESC LIMIT 1")
                .map((row, meta) -> row.get("token", java.util.UUID.class).toString())
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
