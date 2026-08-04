package com.techpulse.techradar.integration;

import com.techpulse.techradar.features.chat.domain.ChatHealthResponse;
import com.techpulse.techradar.features.chat.domain.ChatResponse;
import com.techpulse.techradar.features.chat.ports.ChatPort;
import com.techpulse.techradar.features.clustering.ports.ClusteringServicePort;
import org.junit.jupiter.api.BeforeEach;
import org.neo4j.driver.Driver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Shared harness for the full-stack API integration tests: real Postgres (+Flyway/R2DBC), Neo4j
 * and Redis, each a Testcontainers-managed Docker container - no more hand-starting them via the
 * docker CLI or exporting POSTGRES_HOST/NEO4J_URI/REDIS_HOST first. Every run starts from a fresh
 * container, so the old "rerunning against a still-populated Postgres gives a spurious 409 on
 * register" problem can no longer happen. The Python clustering/RAG ports are still mocked.
 *
 * <p>Containers are started once via the static initializer below (the "singleton container"
 * pattern) rather than through {@code @Testcontainers}/{@code @Container}, whose per-test-class
 * stop-after-class behavior would tear them down again after the first concrete subclass finishes
 * - these fields are shared by every concrete {@code *IntegrationTest}, same as the
 * {@code @SpringBootTest} context Spring's test-context cache boots once and reuses across all of
 * them. Testcontainers' Ryuk reaper stops them when the JVM exits, so no explicit stop() is needed.
 *
 * <p>Not named {@code *Test} on purpose so Surefire's default include pattern doesn't try to run
 * this abstract class directly.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
// All integration tests hit the server from the same loopback address, and this class's helpers
// register/login dozens of times across the full suite — well past the real auth-rate-limit
// thresholds. Raise them here so the limiter (exercised on its own in AuthRateLimiterServiceTest)
// doesn't turn into spurious 429s in tests that have nothing to do with rate limiting.
@TestPropertySource(properties = {
        "app.redis.auth-rate-limit.login.max-requests=100000",
        "app.redis.auth-rate-limit.register.max-requests=100000",
        "app.redis.auth-rate-limit.forgot-password.max-requests=100000",
        // Off in tests so the poller doesn't attempt real Kafka sends (and its own log noise)
        // against a broker most integration tests never start — the outbox write itself (the
        // thing under test) still happens synchronously inside the ETL transaction regardless.
        "app.outbox.relay.enabled=false"
})
abstract class IntegrationTestSupport {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("techradar")
            .withUsername("postgres")
            .withPassword("postgres");

    static final Neo4jContainer<?> NEO4J = new Neo4jContainer<>("neo4j:5")
            .withAdminPassword("password");

    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    static {
        POSTGRES.start();
        NEO4J.start();
        REDIS.start();
    }

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.r2dbc.url", () -> "r2dbc:postgresql://" + POSTGRES.getHost() + ":"
                + POSTGRES.getMappedPort(5432) + "/" + POSTGRES.getDatabaseName());
        registry.add("spring.r2dbc.username", POSTGRES::getUsername);
        registry.add("spring.r2dbc.password", POSTGRES::getPassword);
        // Flyway migrates over a plain JDBC connection (see application.yml), separate from the
        // R2DBC connection the app uses at runtime.
        registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);

        // Neo4jConfig builds its Driver bean straight from these app.neo4j.* properties instead of
        // Spring Boot's own Neo4j auto-configuration, so a @ServiceConnection (which only wires
        // spring.neo4j.*) wouldn't reach it - this explicit override is required instead.
        registry.add("app.neo4j.uri", NEO4J::getBoltUrl);
        registry.add("app.neo4j.username", () -> "neo4j");
        registry.add("app.neo4j.password", NEO4J::getAdminPassword);

        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @LocalServerPort
    int port;

    WebTestClient web;

    @Autowired
    Driver neo4j;

    @Autowired
    DatabaseClient db;

    @Autowired
    ReactiveStringRedisTemplate redisTemplate;

    /** Mirrors {@code GetCompaniesUseCase.CACHE_KEY} — that field isn't public, and this is test-only. */
    private static final String COMPANY_CACHE_KEY = "cache:company:all";

    @MockitoBean
    ClusteringServicePort clusteringPort;

    @MockitoBean
    ChatPort chatPort;

    static final ParameterizedTypeReference<Map<String, Object>> MAP =
            new ParameterizedTypeReference<>() {
            };

    @BeforeEach
    void baseSetup() {
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

    // ---- shared helpers ---------------------------------------------------------

    Map<String, Object> registerUser(String email) {
        return web.post().uri("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("full_name", "Test User", "email", email, "password", "Passw0rd!"))
                .exchange().expectStatus().isCreated()
                .expectBody(MAP).returnResult().getResponseBody();
    }

    String login(String email, String password) {
        Map<String, Object> body = web.post().uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("email", email, "password", password))
                .exchange().expectStatus().isOk()
                .expectBody(MAP).returnResult().getResponseBody();
        return (String) body.get("access_token");
    }

    String registerAndLogin(String email) {
        registerUser(email);
        return login(email, "Passw0rd!");
    }

    String adminToken() {
        return login("admin@techradar.vn", "Admin@12345");
    }

    static String bearer(String token) {
        return "Bearer " + token;
    }

    @SuppressWarnings("unchecked")
    String meId(String token) {
        Map<String, Object> body = web.get().uri("/api/v1/auth/me").header("Authorization", bearer(token))
                .exchange().expectStatus().isOk()
                .expectBody(MAP).returnResult().getResponseBody();
        return (String) body.get("id");
    }

    @SuppressWarnings("unchecked")
    String createChatSession(String token) {
        Map<String, Object> body = web.post().uri("/api/v1/chat/session").header("Authorization", bearer(token))
                .exchange().expectStatus().isOk()
                .expectBody(MAP).returnResult().getResponseBody();
        return (String) ((Map<String, Object>) body.get("data")).get("session_id");
    }

    /** Seed a small knowledge graph and run the analytics ETL so radar/compare have data. */
    void seedAndEtl(String admin) {
        // Start from a clean tech_analytics so assertions don't depend on dev seed sample rows.
        db.sql("DELETE FROM tech_analytics").fetch().rowsUpdated().block();
        seedGraph();
        web.post().uri("/api/v1/admin/analytics/rebuild").header("Authorization", bearer(admin))
                .exchange().expectStatus().isOk();
    }

    void seedGraph() {
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

    /** Minimal Company+Job+Technology graph so GetCompaniesUseCase.all() (Neo4jCompanyRepository)
     *  can resolve {@code companyId} — does NOT wipe existing nodes, unlike {@link #seedGraph()}. */
    void seedCompany(String companyId, String name, String location) {
        try (var session = neo4j.session()) {
            session.run(
                    "CREATE (c:Company {id: $id, name: $name, location: $location}) " +
                    "CREATE (t:Technology {name: $techName}) " +
                    "CREATE (j:Job {title: 'Rust Dev'}) " +
                    "CREATE (j)-[:REQUIRES]->(t) " +
                    "CREATE (j)-[:POSTED_BY]->(c)",
                    Map.of("id", companyId, "name", name, "location", location, "techName", "Rust-" + companyId));
        }
        evictCompanyCache();
    }

    /** Same as {@link #seedCompany(String, String, String)}, also setting c.industry/c.size. */
    void seedCompany(String companyId, String name, String location, String industry, String size) {
        try (var session = neo4j.session()) {
            session.run(
                    "CREATE (c:Company {id: $id, name: $name, location: $location, " +
                    "industry: $industry, size: $size}) " +
                    "CREATE (t:Technology {name: $techName}) " +
                    "CREATE (j:Job {title: 'Rust Dev'}) " +
                    "CREATE (j)-[:REQUIRES]->(t) " +
                    "CREATE (j)-[:POSTED_BY]->(c)",
                    Map.of("id", companyId, "name", name, "location", location,
                            "industry", industry, "size", size, "techName", "Rust-" + companyId));
        }
        evictCompanyCache();
    }

    /** GetCompaniesUseCase.all() caches the Neo4j company list in Redis for 30 min — a test that
     *  seeds a new company directly into Neo4j (bypassing the ingestion pipeline that would
     *  normally invalidate this cache) must evict it too, or a cache populated by an earlier test
     *  in the same run silently hides the just-seeded company. */
    private void evictCompanyCache() {
        redisTemplate.opsForValue().delete(COMPANY_CACHE_KEY).block();
    }

    /** Article-[:MENTIONS]->Company edge, for GET /companies/{id}/mentions. Requires the company
     *  to already exist (see {@link #seedCompany}). */
    void seedCompanyMention(String companyId, String title, String url, String publishDate, String sourcePlatform) {
        try (var session = neo4j.session()) {
            session.run(
                    "MATCH (c:Company {id: $company_id}) " +
                    "CREATE (a:Article {title: $title, source_url: $url, " +
                    "publish_date: $publish_date, source_platform: $source_platform}) " +
                    "CREATE (a)-[:MENTIONS]->(c)",
                    Map.of("company_id", companyId, "title", title, "url", url,
                            "publish_date", publishDate, "source_platform", sourcePlatform));
        }
    }
}
