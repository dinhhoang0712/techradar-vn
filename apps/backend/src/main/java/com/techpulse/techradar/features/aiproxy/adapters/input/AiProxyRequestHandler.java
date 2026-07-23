package com.techpulse.techradar.features.aiproxy.adapters.input;

import com.techpulse.techradar.features.aiproxy.ports.AiProxyPort;
import com.techpulse.techradar.features.system.ports.ActivityLogRepository;
import com.techpulse.techradar.shared.dto.ApiResponse;
import com.techpulse.techradar.shared.exception.RateLimitExceededException;
import com.techpulse.techradar.shared.redis.AiProxyRateLimiterService;
import com.techpulse.techradar.shared.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Shared request/response plumbing for the thin AI-proxy controllers: rate limiting, attaching
 * the current user id (when one is authenticated), forwarding via {@link AiProxyPort}, and
 * wrapping the result (or a 503) as an {@link ApiResponse}. Also logs one {@code ai_request}
 * activity row per call (best-effort — a logging failure must never affect the actual AI
 * response), feeding the admin live-metrics dashboard's "AI requests today" count.
 * <p>
 * Rate limiting happens OUTSIDE {@link #doForward}'s {@code onErrorResume} (which coerces any
 * upstream failure into a generic 503) so a {@link RateLimitExceededException} propagates to
 * {@code GlobalExceptionHandler} as a real 429 instead of being swallowed into a wrong 503.
 */
@Component
@RequiredArgsConstructor
class AiProxyRequestHandler {

    private final AiProxyPort aiProxyPort;
    private final ActivityLogRepository activityLog;
    private final AiProxyRateLimiterService rateLimiter;

    /**
     * Forwards {@code body} with the current user's id attached as {@code user_id}, rate limited
     * per user id. These routes are all auth-required in {@code SecurityConfig}, so
     * {@code currentUserId()} should always resolve — the empty fallback is a defensive no-op
     * that skips rate limiting rather than guessing a key.
     */
    Mono<ResponseEntity<ApiResponse<Map<String, Object>>>> forwardAsCurrentUser(
            String path, Map<String, Object> body, Duration timeout, String successMessage, String unavailableMessage) {
        Map<String, Object> base = body != null ? body : Collections.emptyMap();
        return SecurityUtils.currentUserId()
                .flatMap(userId -> rateLimiter.isAllowedForUser(userId)
                        .flatMap(allowed -> allowed
                                ? doForward(path, withUserId(base, userId), timeout, successMessage, unavailableMessage)
                                : rateLimitExceeded()))
                .switchIfEmpty(Mono.defer(() -> doForward(path, base, timeout, successMessage, unavailableMessage)));
    }

    /** Forwards {@code body} as-is, with no user context attached, rate limited per client IP. */
    Mono<ResponseEntity<ApiResponse<Map<String, Object>>>> forward(
            String path, Map<String, Object> body, Duration timeout, String successMessage, String unavailableMessage,
            String clientIp) {
        Map<String, Object> request = body != null ? body : Collections.emptyMap();
        return rateLimiter.isAllowedForIp(clientIp)
                .flatMap(allowed -> allowed
                        ? doForward(path, request, timeout, successMessage, unavailableMessage)
                        : rateLimitExceeded());
    }

    private Mono<ResponseEntity<ApiResponse<Map<String, Object>>>> doForward(
            String path, Map<String, Object> request, Duration timeout, String successMessage, String unavailableMessage) {
        recordAiRequest();
        return aiProxyPort.forward(path, request, timeout)
                .map(data -> success(data, successMessage))
                .onErrorResume(ex -> unavailable(unavailableMessage));
    }

    private static <T> Mono<T> rateLimitExceeded() {
        return Mono.error(new RateLimitExceededException("AI request rate limit exceeded. Please slow down."));
    }

    private void recordAiRequest() {
        activityLog.recordAiRequest().onErrorResume(e -> Mono.empty()).subscribe();
    }

    private static Map<String, Object> withUserId(Map<String, Object> base, String userId) {
        Map<String, Object> request = new HashMap<>(base);
        request.put("user_id", userId);
        return request;
    }

    private static ResponseEntity<ApiResponse<Map<String, Object>>> success(Map<String, Object> data, String message) {
        return ResponseEntity.ok(ApiResponse.success(data, message));
    }

    private static Mono<ResponseEntity<ApiResponse<Map<String, Object>>>> unavailable(String message) {
        return Mono.just(ResponseEntity.status(503).body(ApiResponse.error(message, "SERVICE_UNAVAILABLE")));
    }
}
