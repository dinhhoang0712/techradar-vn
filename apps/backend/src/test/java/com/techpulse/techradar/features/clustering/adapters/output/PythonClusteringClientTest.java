package com.techpulse.techradar.features.clustering.adapters.output;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.techpulse.techradar.shared.exception.DatabaseUnavailableException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;

import java.time.Duration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.configureFor;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Its own {@link CircuitBreaker} instance is the whole point of {@code mlClusteringCircuitBreaker}
 * (see Resilience4jConfig) — separate from the 4 ai-rag-core clients' shared breaker, so
 * ml-clustering being down never trips the breaker guarding ai-rag-core calls or vice versa.
 */
class PythonClusteringClientTest {

    private WireMockServer wireMockServer;
    private PythonClusteringClient client;
    private CircuitBreaker circuitBreaker;

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(0);
        wireMockServer.start();
        configureFor("localhost", wireMockServer.port());

        circuitBreaker = CircuitBreaker.ofDefaults("test-ml-clustering");
        client = new PythonClusteringClient(WebClient.builder(), circuitBreaker);
        ReflectionTestUtils.setField(client, "clusteringBaseUrl", "http://localhost:" + wireMockServer.port());
        ReflectionTestUtils.setField(client, "timeout", 5000L);
        ReflectionTestUtils.setField(client, "internalToken", "test-token");
    }

    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }

    @Test
    void getTechCluster_parsesResponse() {
        stubFor(get(urlEqualTo("/tech/React/cluster"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"tech_name\":\"React\",\"cluster_id\":0,\"found\":true}")));

        StepVerifier.create(client.getTechCluster("React"))
                .assertNext(response -> assertThat(response.get("cluster_id")).isEqualTo(0))
                .verifyComplete();
    }

    @Test
    void getClusters_wrapsErrorAsDatabaseUnavailable_whenServiceErrors() {
        stubFor(get(urlEqualTo("/clusters")).willReturn(aResponse().withStatus(500)));

        StepVerifier.create(client.getClusters(null))
                .expectErrorMatches(ex -> ex instanceof DatabaseUnavailableException)
                .verify(Duration.ofSeconds(15));
    }

    @Test
    void getTechCluster_failsFastWithoutCallingWireMock_whenCircuitBreakerIsOpen() {
        circuitBreaker.transitionToOpenState();
        stubFor(get(urlEqualTo("/tech/React/cluster")).willReturn(aResponse().withStatus(200)));

        StepVerifier.create(client.getTechCluster("React"))
                .expectErrorMatches(ex -> ex instanceof DatabaseUnavailableException
                        && ex.getMessage().contains("circuit breaker open"))
                .verify(Duration.ofSeconds(5));

        verify(0, getRequestedFor(urlEqualTo("/tech/React/cluster")));
    }
}
