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
class ForecastControllerTest {

    @Mock
    private AiProxyPort aiProxyPort;
    @Mock
    private ActivityLogRepository activityLog;
    @Mock
    private AiProxyRateLimiterService rateLimiter;

    private ForecastController controller;
    private final ServerHttpRequest httpRequest = MockServerHttpRequest.get("/forecast")
            .header("X-Real-IP", "203.0.113.7")
            .build();

    @BeforeEach
    void setUp() {
        lenient().when(activityLog.recordAiRequest()).thenReturn(Mono.empty());
        lenient().when(rateLimiter.isAllowedForIp(any())).thenReturn(Mono.just(true));
        controller = new ForecastController(new AiProxyRequestHandler(aiProxyPort, activityLog, rateLimiter));
    }

    @Test
    void forecast_buildsBodyFromQueryParams_forwardsToForecastPath_andWrapsResponse() {
        Map<String, Object> proxyResponse = Map.of("trend", "growing");
        when(aiProxyPort.forward(eq("/forecast"), any(), any(Duration.class))).thenReturn(Mono.just(proxyResponse));

        StepVerifier.create(controller.forecast("Rust", 12, httpRequest))
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                    ApiResponse<Map<String, Object>> apiResponse = response.getBody();
                    assertThat(apiResponse.isSuccess()).isTrue();
                    assertThat(apiResponse.getData()).isEqualTo(proxyResponse);
                })
                .verifyComplete();

        ArgumentCaptor<Map<String, Object>> requestCaptor = ArgumentCaptor.forClass(Map.class);
        verify(aiProxyPort).forward(eq("/forecast"), requestCaptor.capture(), any(Duration.class));
        assertThat(requestCaptor.getValue()).containsEntry("technology", "Rust").containsEntry("horizon_months", 12);
    }

    @Test
    void forecast_defaultsHorizonMonthsToSix_whenNotProvided() {
        when(aiProxyPort.forward(eq("/forecast"), any(), any(Duration.class))).thenReturn(Mono.just(Map.of()));

        StepVerifier.create(controller.forecast("Go", 6, httpRequest))
                .expectNextCount(1)
                .verifyComplete();

        ArgumentCaptor<Map<String, Object>> requestCaptor = ArgumentCaptor.forClass(Map.class);
        verify(aiProxyPort).forward(eq("/forecast"), requestCaptor.capture(), any(Duration.class));
        assertThat(requestCaptor.getValue()).containsEntry("horizon_months", 6);
    }

    @Test
    void forecast_propagatesRateLimitExceeded_whenIpOverLimit() {
        when(rateLimiter.isAllowedForIp("203.0.113.7")).thenReturn(Mono.just(false));

        StepVerifier.create(controller.forecast("Rust", 6, httpRequest))
                .expectError(com.techpulse.techradar.shared.exception.RateLimitExceededException.class)
                .verify();
    }
}
