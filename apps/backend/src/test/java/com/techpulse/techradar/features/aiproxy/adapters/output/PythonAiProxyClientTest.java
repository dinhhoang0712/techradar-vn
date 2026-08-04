package com.techpulse.techradar.features.aiproxy.adapters.output;

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
import java.util.Map;

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

class PythonAiProxyClientTest {

    private WireMockServer wireMockServer;
    private PythonAiProxyClient client;
    private CircuitBreaker circuitBreaker;

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(0);
        wireMockServer.start();
        configureFor("localhost", wireMockServer.port());

        circuitBreaker = CircuitBreaker.ofDefaults("test-ai-rag-core");
        client = new PythonAiProxyClient(WebClient.builder(), circuitBreaker);
        ReflectionTestUtils.setField(client, "aiBaseUrl", "http://localhost:" + wireMockServer.port());
        ReflectionTestUtils.setField(client, "internalToken", "test-token");
    }

    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }

    @Test
    void forward_postsBodyAndInternalAuthHeader_andParsesResponse() {
        stubFor(post(urlEqualTo("/career"))
                .withHeader("X-Internal-Auth", equalTo("test-token"))
                .withRequestBody(matchingJsonPath("$.target_role", equalTo("Backend Engineer")))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"roadmap\":[\"Java\",\"Spring\"]}")));

        StepVerifier.create(client.forward("/career", Map.of("target_role", "Backend Engineer"), Duration.ofSeconds(5)))
                .assertNext(response -> assertThat(response).containsKey("roadmap"))
                .verifyComplete();

        verify(postRequestedFor(urlEqualTo("/career")));
    }

    @Test
    void forward_wrapsErrorAsDatabaseUnavailable_whenServiceErrors() {
        stubFor(post(urlEqualTo("/career"))
                .willReturn(aResponse().withStatus(500)));

        StepVerifier.create(client.forward("/career", Map.of(), Duration.ofSeconds(5)))
                .expectErrorMatches(ex -> ex instanceof DatabaseUnavailableException)
                .verify(Duration.ofSeconds(15));
    }

    @Test
    void forward_wrapsErrorAsDatabaseUnavailable_whenRequestTimesOut() {
        stubFor(post(urlEqualTo("/career"))
                .willReturn(aResponse().withStatus(200).withFixedDelay(500)));

        StepVerifier.create(client.forward("/career", Map.of(), Duration.ofMillis(100)))
                .expectErrorMatches(ex -> ex instanceof DatabaseUnavailableException)
                .verify(Duration.ofSeconds(15));
    }

    @Test
    void forward_failsFastWithoutCallingWireMock_whenCircuitBreakerIsOpen() {
        circuitBreaker.transitionToOpenState();
        stubFor(post(urlEqualTo("/career")).willReturn(aResponse().withStatus(200)));

        StepVerifier.create(client.forward("/career", Map.of(), Duration.ofSeconds(5)))
                .expectErrorMatches(ex -> ex instanceof DatabaseUnavailableException
                        && ex.getMessage().contains("circuit breaker open"))
                .verify(Duration.ofSeconds(5));

        verify(0, postRequestedFor(urlEqualTo("/career")));
    }
}
