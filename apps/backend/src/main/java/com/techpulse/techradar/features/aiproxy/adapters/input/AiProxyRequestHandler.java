package com.techpulse.techradar.features.aiproxy.adapters.input;

import com.techpulse.techradar.features.aiproxy.ports.AiProxyPort;
import com.techpulse.techradar.shared.dto.ApiResponse;
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
 * Shared request/response plumbing for the thin AI-proxy controllers: attaching the current
 * user id (when one is authenticated), forwarding via {@link AiProxyPort}, and wrapping the
 * result (or a 503) as an {@link ApiResponse}.
 */
@Component
@RequiredArgsConstructor
class AiProxyRequestHandler {

    private final AiProxyPort aiProxyPort;

    /** Forwards {@code body} with the current user's id attached as {@code user_id}. */
    Mono<ResponseEntity<ApiResponse<Map<String, Object>>>> forwardAsCurrentUser(
            String path, Map<String, Object> body, Duration timeout, String successMessage, String unavailableMessage) {
        Map<String, Object> base = body != null ? body : Collections.emptyMap();
        return SecurityUtils.currentUserId()
                .map(userId -> withUserId(base, userId))
                .defaultIfEmpty(base)
                .flatMap(request -> aiProxyPort.forward(path, request, timeout))
                .map(data -> success(data, successMessage))
                .onErrorResume(ex -> unavailable(unavailableMessage));
    }

    /** Forwards {@code body} as-is, with no user context attached. */
    Mono<ResponseEntity<ApiResponse<Map<String, Object>>>> forward(
            String path, Map<String, Object> body, Duration timeout, String successMessage, String unavailableMessage) {
        Map<String, Object> request = body != null ? body : Collections.emptyMap();
        return aiProxyPort.forward(path, request, timeout)
                .map(data -> success(data, successMessage))
                .onErrorResume(ex -> unavailable(unavailableMessage));
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
