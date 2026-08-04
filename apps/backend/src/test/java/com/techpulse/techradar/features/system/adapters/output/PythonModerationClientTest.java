package com.techpulse.techradar.features.system.adapters.output;

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
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;

class PythonModerationClientTest {

    private WireMockServer wireMockServer;
    private PythonModerationClient client;
    private CircuitBreaker circuitBreaker;

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(0);
        wireMockServer.start();
        configureFor("localhost", wireMockServer.port());

        circuitBreaker = CircuitBreaker.ofDefaults("test-ai-rag-core");
        client = new PythonModerationClient(WebClient.builder(), circuitBreaker);
        ReflectionTestUtils.setField(client, "pythonAiBaseUrl", "http://localhost:" + wireMockServer.port());
        ReflectionTestUtils.setField(client, "timeout", 5000L);
        ReflectionTestUtils.setField(client, "internalToken", "test-token");
    }

    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }

    @Test
    void suggest_parsesResponseAndSendsInternalAuthHeader() {
        stubFor(post(urlEqualTo("/internal/ai/moderation-suggestion"))
                .withHeader("X-Internal-Auth", equalTo("test-token"))
                .withRequestBody(matchingJsonPath("$.target_type", equalTo("POST")))
                .withRequestBody(matchingJsonPath("$.target_content", equalTo("nội dung bị báo cáo")))
                .withRequestBody(matchingJsonPath("$.report_reason", equalTo("spam quảng cáo")))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"action\":\"REMOVE\",\"reason\":\"Spam quang cao.\",\"confidence\":0.87}")));

        StepVerifier.create(client.suggest("POST", "nội dung bị báo cáo", "spam quảng cáo"))
                .assertNext(s -> {
                    assertThat(s.action()).isEqualTo("REMOVE");
                    assertThat(s.reason()).isEqualTo("Spam quang cao.");
                    assertThat(s.confidence()).isEqualTo(0.87);
                })
                .verifyComplete();

        verify(postRequestedFor(urlEqualTo("/internal/ai/moderation-suggestion")));
    }

    @Test
    void suggest_wrapsErrorAsDatabaseUnavailable_whenServiceErrors() {
        stubFor(post(urlEqualTo("/internal/ai/moderation-suggestion"))
                .willReturn(aResponse().withStatus(500)));

        StepVerifier.create(client.suggest("POST", "content", "reason"))
                .expectErrorMatches(ex -> ex instanceof DatabaseUnavailableException)
                .verify(Duration.ofSeconds(15));
    }

    @Test
    void suggest_failsFastWithoutCallingWireMock_whenCircuitBreakerIsOpen() {
        circuitBreaker.transitionToOpenState();
        stubFor(post(urlEqualTo("/internal/ai/moderation-suggestion")).willReturn(aResponse().withStatus(200)));

        StepVerifier.create(client.suggest("POST", "content", "reason"))
                .expectErrorMatches(ex -> ex instanceof DatabaseUnavailableException
                        && ex.getMessage().contains("circuit breaker open"))
                .verify(Duration.ofSeconds(5));

        verify(0, postRequestedFor(urlEqualTo("/internal/ai/moderation-suggestion")));
    }
}
