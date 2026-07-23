package com.techpulse.techradar.features.aiproxy.adapters.input;

import com.techpulse.techradar.features.aiproxy.ports.AiProxyPort;
import com.techpulse.techradar.features.system.ports.ActivityLogRepository;
import com.techpulse.techradar.shared.dto.ApiResponse;
import com.techpulse.techradar.shared.redis.AiProxyRateLimiterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportControllerTest {

    @Mock
    private AiProxyPort aiProxyPort;
    @Mock
    private ActivityLogRepository activityLog;
    @Mock
    private AiProxyRateLimiterService rateLimiter;

    private ReportController controller;
    private final ServerHttpRequest httpRequest = MockServerHttpRequest.get("/report")
            .header("X-Real-IP", "203.0.113.7")
            .build();

    @BeforeEach
    void setUp() {
        lenient().when(activityLog.recordAiRequest()).thenReturn(Mono.empty());
        lenient().when(rateLimiter.isAllowedForIp(any())).thenReturn(Mono.just(true));
        controller = new ReportController(new AiProxyRequestHandler(aiProxyPort, activityLog, rateLimiter));
    }

    @Test
    void report_buildsBodyFromQueryParams_forwardsToReportPath_andWrapsResponse() {
        Map<String, Object> proxyResponse = Map.of("report", "# Q1 report");
        when(aiProxyPort.forward(eq("/report"), any(), any(Duration.class))).thenReturn(Mono.just(proxyResponse));

        StepVerifier.create(controller.report("2026-Q1", 5, "html", httpRequest))
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                    ApiResponse<Map<String, Object>> apiResponse = response.getBody();
                    assertThat(apiResponse.isSuccess()).isTrue();
                    assertThat(apiResponse.getData()).isEqualTo(proxyResponse);
                })
                .verifyComplete();

        ArgumentCaptor<Map<String, Object>> requestCaptor = ArgumentCaptor.forClass(Map.class);
        verify(aiProxyPort).forward(eq("/report"), requestCaptor.capture(), any(Duration.class));
        assertThat(requestCaptor.getValue())
                .containsEntry("period", "2026-Q1")
                .containsEntry("top_n", 5)
                .containsEntry("format", "html");
    }

    @Test
    void report_usesDefaultTopNAndFormat_whenNotProvided() {
        when(aiProxyPort.forward(eq("/report"), any(), any(Duration.class))).thenReturn(Mono.just(Map.of()));

        StepVerifier.create(controller.report("2026-Q1", 10, "markdown", httpRequest))
                .expectNextCount(1)
                .verifyComplete();

        ArgumentCaptor<Map<String, Object>> requestCaptor = ArgumentCaptor.forClass(Map.class);
        verify(aiProxyPort).forward(eq("/report"), requestCaptor.capture(), any(Duration.class));
        assertThat(requestCaptor.getValue()).containsEntry("top_n", 10).containsEntry("format", "markdown");
    }

    @Test
    void report_returns503_whenReportServiceUnavailable() {
        when(aiProxyPort.forward(eq("/report"), any(), any(Duration.class)))
                .thenReturn(Mono.error(new RuntimeException("connection refused")));

        StepVerifier.create(controller.report("2026-Q1", 10, "markdown", httpRequest))
                .assertNext(response -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE))
                .verifyComplete();
    }
}
