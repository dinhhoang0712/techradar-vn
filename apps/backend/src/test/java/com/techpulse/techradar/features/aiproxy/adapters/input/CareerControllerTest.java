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
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CareerControllerTest {

    @Mock
    private AiProxyPort aiProxyPort;
    @Mock
    private ActivityLogRepository activityLog;
    @Mock
    private AiProxyRateLimiterService rateLimiter;

    private CareerController controller;

    @BeforeEach
    void setUp() {
        lenient().when(activityLog.recordAiRequest()).thenReturn(Mono.empty());
        lenient().when(rateLimiter.isAllowedForUser(any())).thenReturn(Mono.just(true));
        controller = new CareerController(new AiProxyRequestHandler(aiProxyPort, activityLog, rateLimiter));
    }

    private static reactor.util.context.Context authenticatedAs(String userId) {
        return ReactiveSecurityContextHolder.withAuthentication(
                new TestingAuthenticationToken(userId, null, List.of()));
    }

    @Test
    void career_forwardsToCareerPath_withUserIdAttached_andWrapsResponse() {
        Map<String, Object> body = Map.of("target_role", "Backend Engineer");
        Map<String, Object> proxyResponse = Map.of("roadmap", List.of("Java", "Spring"));
        when(aiProxyPort.forward(eq("/career"), any(), any(Duration.class))).thenReturn(Mono.just(proxyResponse));

        StepVerifier.create(controller.career(body).contextWrite(authenticatedAs("user-1")))
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                    ApiResponse<Map<String, Object>> apiResponse = response.getBody();
                    assertThat(apiResponse.isSuccess()).isTrue();
                    assertThat(apiResponse.getData()).isEqualTo(proxyResponse);
                })
                .verifyComplete();

        ArgumentCaptor<Map<String, Object>> requestCaptor = ArgumentCaptor.forClass(Map.class);
        verify(aiProxyPort).forward(eq("/career"), requestCaptor.capture(), any(Duration.class));
        assertThat(requestCaptor.getValue()).containsEntry("user_id", "user-1").containsEntry("target_role", "Backend Engineer");
    }

    @Test
    void career_returns503_whenCareerServiceUnavailable() {
        when(aiProxyPort.forward(eq("/career"), any(), any(Duration.class)))
                .thenReturn(Mono.error(new RuntimeException("connection refused")));

        StepVerifier.create(controller.career(Map.of()).contextWrite(authenticatedAs("user-1")))
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(response.getBody().getErrorCode()).isEqualTo("SERVICE_UNAVAILABLE");
                })
                .verifyComplete();
    }
}
