package com.techpulse.techradar.features.aiproxy.adapters.input;

import com.techpulse.techradar.features.aiproxy.ports.AiProxyPort;
import com.techpulse.techradar.features.system.ports.ActivityLogRepository;
import com.techpulse.techradar.shared.exception.RateLimitExceededException;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the rate-limit gating {@link AiProxyRequestHandler} adds on top of the plain
 * forward/wrap plumbing (already exercised indirectly via {@code CompanyInsightControllerTest}):
 * a throttled request must propagate {@link RateLimitExceededException} untouched (NOT get
 * coerced into the generic 503 the upstream-failure {@code onErrorResume} produces), and an
 * allowed request must still forward and record metrics exactly as before.
 */
@ExtendWith(MockitoExtension.class)
class AiProxyRequestHandlerTest {

    @Mock
    private AiProxyPort aiProxyPort;
    @Mock
    private ActivityLogRepository activityLog;
    @Mock
    private AiProxyRateLimiterService rateLimiter;

    private AiProxyRequestHandler handler;

    @BeforeEach
    void setUp() {
        lenient().when(activityLog.recordAiRequest()).thenReturn(Mono.empty());
        handler = new AiProxyRequestHandler(aiProxyPort, activityLog, rateLimiter);
    }

    private static reactor.util.context.Context authenticatedAs(String userId) {
        return ReactiveSecurityContextHolder.withAuthentication(
                new TestingAuthenticationToken(userId, null, List.of()));
    }

    @Test
    void forwardAsCurrentUser_forwardsWithUserIdAttached_whenAllowed() {
        Map<String, Object> body = Map.of("target_role", "Backend Engineer");
        Map<String, Object> proxyResponse = Map.of("roadmap", List.of("Java", "Spring"));
        when(rateLimiter.isAllowedForUser("user-1")).thenReturn(Mono.just(true));
        when(aiProxyPort.forward(eq("/career"), any(), any(Duration.class))).thenReturn(Mono.just(proxyResponse));

        StepVerifier.create(handler.forwardAsCurrentUser("/career", body, AiProxyPort.DEFAULT_TIMEOUT,
                        "Career advice generated", "Career service unavailable")
                        .contextWrite(authenticatedAs("user-1")))
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                    assertThat(response.getBody().getData()).isEqualTo(proxyResponse);
                })
                .verifyComplete();

        ArgumentCaptor<Map<String, Object>> requestCaptor = ArgumentCaptor.forClass(Map.class);
        verify(aiProxyPort).forward(eq("/career"), requestCaptor.capture(), any(Duration.class));
        assertThat(requestCaptor.getValue()).containsEntry("user_id", "user-1").containsEntry("target_role", "Backend Engineer");
        verify(activityLog).recordAiRequest();
    }

    @Test
    void forwardAsCurrentUser_propagatesRateLimitExceeded_withoutCallingProxyOrRecordingMetrics() {
        when(rateLimiter.isAllowedForUser("user-1")).thenReturn(Mono.just(false));

        StepVerifier.create(handler.forwardAsCurrentUser("/career", Map.of(), AiProxyPort.DEFAULT_TIMEOUT,
                        "Career advice generated", "Career service unavailable")
                        .contextWrite(authenticatedAs("user-1")))
                .expectError(RateLimitExceededException.class)
                .verify();

        verify(aiProxyPort, never()).forward(any(), any(), any());
        verify(activityLog, never()).recordAiRequest();
    }

    @Test
    void forwardAsCurrentUser_stillForwards_whenNoAuthenticatedUserPresent() {
        Map<String, Object> proxyResponse = Map.of("summary", "ok");
        when(aiProxyPort.forward(eq("/career"), eq(Map.of()), any(Duration.class))).thenReturn(Mono.just(proxyResponse));

        StepVerifier.create(handler.forwardAsCurrentUser("/career", Map.of(), AiProxyPort.DEFAULT_TIMEOUT,
                        "Career advice generated", "Career service unavailable"))
                .assertNext(response -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK))
                .verifyComplete();

        verify(rateLimiter, never()).isAllowedForUser(any());
    }

    @Test
    void forwardAsCurrentUser_returns503_whenProxyFails() {
        when(rateLimiter.isAllowedForUser("user-1")).thenReturn(Mono.just(true));
        when(aiProxyPort.forward(eq("/career"), any(), any(Duration.class)))
                .thenReturn(Mono.error(new RuntimeException("connection refused")));

        StepVerifier.create(handler.forwardAsCurrentUser("/career", Map.of(), AiProxyPort.DEFAULT_TIMEOUT,
                        "Career advice generated", "Career service unavailable")
                        .contextWrite(authenticatedAs("user-1")))
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(response.getBody().getErrorCode()).isEqualTo("SERVICE_UNAVAILABLE");
                })
                .verifyComplete();
    }

    @Test
    void forward_forwardsBodyAsIs_whenIpAllowed() {
        Map<String, Object> body = Map.of("technology", "Rust");
        Map<String, Object> proxyResponse = Map.of("forecast", "growing");
        when(rateLimiter.isAllowedForIp("203.0.113.7")).thenReturn(Mono.just(true));
        when(aiProxyPort.forward(eq("/forecast"), eq(body), any(Duration.class))).thenReturn(Mono.just(proxyResponse));

        StepVerifier.create(handler.forward("/forecast", body, AiProxyPort.DEFAULT_TIMEOUT,
                        "Forecast generated", "Forecast service unavailable", "203.0.113.7"))
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                    assertThat(response.getBody().getData()).isEqualTo(proxyResponse);
                })
                .verifyComplete();

        verify(activityLog).recordAiRequest();
    }

    @Test
    void forward_propagatesRateLimitExceeded_withoutCallingProxy_whenIpOverLimit() {
        when(rateLimiter.isAllowedForIp("203.0.113.7")).thenReturn(Mono.just(false));

        StepVerifier.create(handler.forward("/forecast", Map.of(), AiProxyPort.DEFAULT_TIMEOUT,
                        "Forecast generated", "Forecast service unavailable", "203.0.113.7"))
                .expectError(RateLimitExceededException.class)
                .verify();

        verify(aiProxyPort, never()).forward(any(), any(), any());
        verify(activityLog, never()).recordAiRequest();
    }

    @Test
    void forward_returns503_whenProxyFails() {
        when(rateLimiter.isAllowedForIp("203.0.113.7")).thenReturn(Mono.just(true));
        when(aiProxyPort.forward(eq("/forecast"), any(), any(Duration.class)))
                .thenReturn(Mono.error(new RuntimeException("timeout")));

        StepVerifier.create(handler.forward("/forecast", Map.of(), AiProxyPort.DEFAULT_TIMEOUT,
                        "Forecast generated", "Forecast service unavailable", "203.0.113.7"))
                .assertNext(response -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE))
                .verifyComplete();
    }
}
