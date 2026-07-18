package com.techpulse.techradar.features.system.adapters.output;

import com.techpulse.techradar.features.system.ports.ModerationSuggestionPort;
import com.techpulse.techradar.shared.exception.DatabaseUnavailableException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Python AI client adapter for content-moderation suggestions.
 * Communicates with Python FastAPI service via HTTP.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PythonModerationClient implements ModerationSuggestionPort {

    private final WebClient.Builder webClientBuilder;

    @Value("${app.python.ai.base-url:http://localhost:8000}")
    private String pythonAiBaseUrl;

    @Value("${app.python.ai.timeout:60000}")
    private long timeout;

    @Value("${app.python.internal-token:}")
    private String internalToken;

    @Override
    public Mono<Suggestion> suggest(String targetType, String targetContent, String reportReason) {
        Map<String, Object> request = new HashMap<>();
        request.put("target_type", targetType);
        request.put("target_content", targetContent);
        request.put("report_reason", reportReason);

        return webClientBuilder.build()
                .post()
                .uri(pythonAiBaseUrl + "/internal/ai/moderation-suggestion")
                .headers(h -> {
                    if (internalToken != null && !internalToken.isBlank()) {
                        h.set("X-Internal-Auth", internalToken);
                    }
                })
                .bodyValue(request)
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> new Suggestion(
                        (String) response.get("action"),
                        (String) response.get("reason"),
                        ((Number) response.get("confidence")).doubleValue()
                ))
                .retryWhen(Retry.backoff(3, Duration.ofSeconds(1)))
                .timeout(Duration.ofMillis(timeout))
                .onErrorResume(ex -> {
                    log.error("Failed to generate moderation suggestion from Python AI service", ex);
                    return Mono.error(
                            new DatabaseUnavailableException(
                                    "AI service unavailable: " + ex.getMessage()
                            )
                    );
                });
    }
}
