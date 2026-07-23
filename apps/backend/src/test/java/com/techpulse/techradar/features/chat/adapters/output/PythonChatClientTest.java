package com.techpulse.techradar.features.chat.adapters.output;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.techpulse.techradar.features.chat.domain.ChatRequest;
import com.techpulse.techradar.shared.exception.DatabaseUnavailableException;
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
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

class PythonChatClientTest {

    private WireMockServer wireMockServer;
    private PythonChatClient client;

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(0);
        wireMockServer.start();
        configureFor("localhost", wireMockServer.port());

        client = new PythonChatClient(WebClient.builder());
        ReflectionTestUtils.setField(client, "pythonRagBaseUrl", "http://localhost:" + wireMockServer.port());
        ReflectionTestUtils.setField(client, "timeout", 5000L);
        ReflectionTestUtils.setField(client, "internalToken", "test-token");
    }

    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }

    @Test
    void getHealth_parsesResponse_andSendsInternalAuthHeader() {
        stubFor(get(urlEqualTo("/health"))
                .withHeader("X-Internal-Auth", equalTo("test-token"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"status\":\"ok\",\"neo4j\":true,\"version\":\"1.0\"}")));

        StepVerifier.create(client.getHealth())
                .assertNext(health -> {
                    assertThat(health.getStatus()).isEqualTo("ok");
                    assertThat(health.isNeo4j()).isTrue();
                    assertThat(health.getVersion()).isEqualTo("1.0");
                })
                .verifyComplete();
    }

    @Test
    void chat_postsSnakeCaseBody_andParsesResponse() {
        stubFor(post(urlEqualTo("/chat"))
                .withRequestBody(matchingJsonPath("$.query", equalTo("hello")))
                .withRequestBody(matchingJsonPath("$.session_id", equalTo("sid-1")))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"answer\":\"hi there\",\"session_id\":\"sid-1\",\"query\":\"hello\"}")));

        StepVerifier.create(client.chat(new ChatRequest("hello", "sid-1", "user-1")))
                .assertNext(response -> {
                    assertThat(response.getAnswer()).isEqualTo("hi there");
                    assertThat(response.getSessionId()).isEqualTo("sid-1");
                })
                .verifyComplete();
    }

    @Test
    void chat_wrapsErrorAsDatabaseUnavailable_whenServiceErrors() {
        stubFor(post(urlEqualTo("/chat")).willReturn(aResponse().withStatus(500)));

        StepVerifier.create(client.chat(new ChatRequest("hello", null, "user-1")))
                .expectErrorMatches(ex -> ex instanceof DatabaseUnavailableException)
                .verify(Duration.ofSeconds(15));
    }

    @Test
    void listMessages_parsesMessageHistory() {
        stubFor(get(urlEqualTo("/chat/session/sid-1/messages"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("[{\"id\":\"11111111-1111-1111-1111-111111111111\",\"role\":\"user\",\"content\":\"hi\"}]")));

        StepVerifier.create(client.listMessages("sid-1"))
                .assertNext(item -> {
                    assertThat(item.getRole()).isEqualTo("user");
                    assertThat(item.getContent()).isEqualTo("hi");
                })
                .verifyComplete();
    }
}
