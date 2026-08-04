package com.techpulse.techradar.features.compare.adapters.output;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.techpulse.techradar.features.compare.domain.TechComparison;
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
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;

class PythonAiClientTest {

    private WireMockServer wireMockServer;
    private PythonAiClient client;
    private CircuitBreaker circuitBreaker;

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(0);
        wireMockServer.start();
        configureFor("localhost", wireMockServer.port());

        circuitBreaker = CircuitBreaker.ofDefaults("test-ai-rag-core");
        client = new PythonAiClient(WebClient.builder(), circuitBreaker);
        ReflectionTestUtils.setField(client, "pythonAiBaseUrl", "http://localhost:" + wireMockServer.port());
        ReflectionTestUtils.setField(client, "timeout", 5000L);
        ReflectionTestUtils.setField(client, "internalToken", "test-token");
    }

    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }

    private static TechComparison comparison() {
        return TechComparison.builder()
                .technology1("Rust")
                .technology2("Go")
                .growthRate1(0.5)
                .growthRate2(0.3)
                .jobCount1(10)
                .jobCount2(20)
                .articleCount1(5)
                .articleCount2(8)
                .build();
    }

    @Test
    void generateSummary_postsComparisonFields_andExtractsSummaryFromResponse() {
        stubFor(post(urlEqualTo("/internal/ai/llm-summary"))
                .withHeader("X-Internal-Auth", equalTo("test-token"))
                .withRequestBody(matchingJsonPath("$.tech1", equalTo("Rust")))
                .withRequestBody(matchingJsonPath("$.tech2", equalTo("Go")))
                .withRequestBody(matchingJsonPath("$.job_count_1", equalTo("10")))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"summary\":\"Rust is trending up.\"}")));

        StepVerifier.create(client.generateSummary(comparison()))
                .expectNext("Rust is trending up.")
                .verifyComplete();

        verify(postRequestedFor(urlEqualTo("/internal/ai/llm-summary")));
    }

    @Test
    void generateSummary_wrapsErrorAsDatabaseUnavailable_afterRetriesExhausted() {
        stubFor(post(urlEqualTo("/internal/ai/llm-summary"))
                .willReturn(aResponse().withStatus(500)));

        StepVerifier.create(client.generateSummary(comparison()))
                .expectErrorMatches(ex -> ex instanceof DatabaseUnavailableException
                        && ex.getMessage().startsWith("AI service unavailable:"))
                .verify(Duration.ofSeconds(30));
    }

    @Test
    void generateSummary_failsFastWithoutCallingWireMock_whenCircuitBreakerIsOpen() {
        circuitBreaker.transitionToOpenState();
        stubFor(post(urlEqualTo("/internal/ai/llm-summary")).willReturn(aResponse().withStatus(200)));

        StepVerifier.create(client.generateSummary(comparison()))
                .expectErrorMatches(ex -> ex instanceof DatabaseUnavailableException
                        && ex.getMessage().contains("circuit breaker open"))
                .verify(Duration.ofSeconds(5));

        verify(0, postRequestedFor(urlEqualTo("/internal/ai/llm-summary")));
    }
}
