package com.techpulse.techradar.integration;

import com.techpulse.techradar.features.chat.adapters.input.dto.ChatHealthResponse;
import com.techpulse.techradar.features.chat.adapters.input.dto.ChatResponse;
import com.techpulse.techradar.features.chat.ports.ChatPort;
import com.techpulse.techradar.features.clustering.ports.ClusteringServicePort;
import org.junit.jupiter.api.BeforeEach;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Shared harness for the full-stack API integration tests: real Postgres (+Flyway/R2DBC) and
 * Neo4j, started externally via the docker CLI and wired through the POSTGRES and NEO4J env vars
 * the app already reads. The Python clustering/RAG ports are mocked. Every subclass gets the same
 * {@code @SpringBootTest} configuration so Spring's test context cache boots it once and reuses it
 * across all of them, regardless of how many domain classes extend this.
 *
 * <p>Not named {@code *Test} on purpose so Surefire's default include pattern doesn't try to run
 * this abstract class directly.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIfEnvironmentVariable(named = "POSTGRES_HOST", matches = ".+")  // needs external Postgres + Neo4j (see scratchpad/run_it.sh)
abstract class IntegrationTestSupport {

    @LocalServerPort
    int port;

    WebTestClient web;

    @Autowired
    Driver neo4j;

    @Autowired
    DatabaseClient db;

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
