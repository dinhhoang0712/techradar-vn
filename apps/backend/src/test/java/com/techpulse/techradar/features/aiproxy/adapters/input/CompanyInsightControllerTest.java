package com.techpulse.techradar.features.aiproxy.adapters.input;

import com.techpulse.techradar.features.aiproxy.ports.AiProxyPort;
import com.techpulse.techradar.features.system.ports.ActivityLogRepository;
import com.techpulse.techradar.shared.dto.ApiResponse;
import com.techpulse.techradar.shared.exception.RateLimitExceededException;
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
class CompanyInsightControllerTest {

    @Mock
    private AiProxyPort aiProxyPort;
    @Mock
    private ActivityLogRepository activityLog;
    @Mock
    private AiProxyRateLimiterService rateLimiter;

    private CompanyInsightController controller;
    private final ServerHttpRequest httpRequest = MockServerHttpRequest.post("/company-insight")
            .header("X-Real-IP", "203.0.113.7")
            .build();

    @BeforeEach
    void setUp() {
        lenient().when(activityLog.recordAiRequest()).thenReturn(Mono.empty());
        lenient().when(rateLimiter.isAllowedForIp(any())).thenReturn(Mono.just(true));
        controller = new CompanyInsightController(new AiProxyRequestHandler(aiProxyPort, activityLog, rateLimiter));
    }

    @Test
    void insight_forwardsBodyAsIsAndWrapsTheProxyResponse() {
        Map<String, Object> body = Map.of("company_name", "Acme Corp");
        Map<String, Object> proxyResponse = Map.of("summary", "Acme đang mở rộng đội backend.", "highlights", java.util.List.of());
        when(aiProxyPort.forward(eq("/company-insight"), eq(body), any(Duration.class)))
                .thenReturn(Mono.just(proxyResponse));

        StepVerifier.create(controller.insight(body, httpRequest))
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                    ApiResponse<Map<String, Object>> apiResponse = response.getBody();
                    assertThat(apiResponse).isNotNull();
                    assertThat(apiResponse.isSuccess()).isTrue();
                    assertThat(apiResponse.getData()).isEqualTo(proxyResponse);
                })
                .verifyComplete();
    }

    @Test
    void insight_returns503WhenThePythonServiceIsUnavailable() {
        Map<String, Object> body = Map.of("company_name", "Acme Corp");
        when(aiProxyPort.forward(eq("/company-insight"), eq(body), any(Duration.class)))
                .thenReturn(Mono.error(new RuntimeException("connection refused")));

        StepVerifier.create(controller.insight(body, httpRequest))
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(response.getBody().isSuccess()).isFalse();
                    assertThat(response.getBody().getErrorCode()).isEqualTo("SERVICE_UNAVAILABLE");
                })
                .verifyComplete();
    }

    @Test
    void insight_propagatesRateLimitExceeded_whenIpOverLimit() {
        Map<String, Object> body = Map.of("company_name", "Acme Corp");
        when(rateLimiter.isAllowedForIp("203.0.113.7")).thenReturn(Mono.just(false));

        StepVerifier.create(controller.insight(body, httpRequest))
                .expectError(RateLimitExceededException.class)
                .verify();
    }
}
