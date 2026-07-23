package com.techpulse.techradar.features.aiproxy.adapters.input;

import com.techpulse.techradar.features.aiproxy.ports.AiProxyPort;
import com.techpulse.techradar.features.system.ports.ActivityLogRepository;
import com.techpulse.techradar.shared.dto.ApiResponse;
import com.techpulse.techradar.shared.redis.AiProxyRateLimiterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SummarizeControllerTest {

    @Mock
    private AiProxyPort aiProxyPort;
    @Mock
    private ActivityLogRepository activityLog;
    @Mock
    private AiProxyRateLimiterService rateLimiter;

    private SummarizeController controller;
    private final ServerHttpRequest httpRequest = MockServerHttpRequest.post("/chat/summarize")
            .header("X-Real-IP", "203.0.113.7")
            .build();

    @BeforeEach
    void setUp() {
        lenient().when(activityLog.recordAiRequest()).thenReturn(Mono.empty());
        lenient().when(rateLimiter.isAllowedForIp(any())).thenReturn(Mono.just(true));
        controller = new SummarizeController(new AiProxyRequestHandler(aiProxyPort, activityLog, rateLimiter));
    }

    @Test
    void summarize_forwardsBodyToSummarizePath_andWrapsResponse() {
        Map<String, Object> body = Map.of("period", "2026-07");
        Map<String, Object> proxyResponse = Map.of("summary", "Rust adoption grew this month.");
        when(aiProxyPort.forward(eq("/summarize"), eq(body), any(Duration.class))).thenReturn(Mono.just(proxyResponse));

        StepVerifier.create(controller.summarize(body, httpRequest))
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                    ApiResponse<Map<String, Object>> apiResponse = response.getBody();
                    assertThat(apiResponse.isSuccess()).isTrue();
                    assertThat(apiResponse.getData()).isEqualTo(proxyResponse);
                })
                .verifyComplete();
    }

    @Test
    void summarize_forwardsEmptyMap_whenRequestBodyOmitted() {
        when(aiProxyPort.forward(eq("/summarize"), eq(Map.of()), any(Duration.class))).thenReturn(Mono.just(Map.of()));

        StepVerifier.create(controller.summarize(null, httpRequest))
                .expectNextCount(1)
                .verifyComplete();
    }

    @Test
    void summarize_returns503_whenSummarizeServiceUnavailable() {
        when(aiProxyPort.forward(eq("/summarize"), any(), any(Duration.class)))
                .thenReturn(Mono.error(new RuntimeException("connection refused")));

        StepVerifier.create(controller.summarize(Map.of(), httpRequest))
                .assertNext(response -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE))
                .verifyComplete();
    }
}
