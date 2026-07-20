package com.techpulse.techradar.features.system.adapters.output;

import com.techpulse.techradar.features.system.ports.ModerationSuggestionPort;
import com.techpulse.techradar.shared.client.PythonServiceWebClientFactory;
import com.techpulse.techradar.shared.http.AbstractPythonServiceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

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
public class PythonModerationClient extends AbstractPythonServiceClient implements ModerationSuggestionPort {

    private final WebClient.Builder webClientBuilder;

    @Value("${app.python.ai.base-url:http://localhost:8000}")
    private String pythonAiBaseUrl;

    @Value("${app.python.ai.timeout:60000}")
    private long timeout;

    @Value("${app.python.internal-token:}")
    private String internalToken;

    private WebClient client() {
        return PythonServiceWebClientFactory.build(webClientBuilder, pythonAiBaseUrl, internalToken);
    }

    @Override
    public Mono<Suggestion> suggest(String targetType, String targetContent, String reportReason) {
        Map<String, Object> body = new HashMap<>();
        body.put("target_type", targetType);
        body.put("target_content", targetContent);
        body.put("report_reason", reportReason);

        Mono<Suggestion> request = client()
                .post()
                .uri("/internal/ai/moderation-suggestion")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> new Suggestion(
                        (String) response.get("action"),
                        (String) response.get("reason"),
                        ((Number) response.get("confidence")).doubleValue()
                ));

        return mapMono(request, true, Duration.ofMillis(timeout),
                ex -> log.error("Failed to generate moderation suggestion from Python AI service", ex),
                ex -> "AI service unavailable: " + ex.getMessage());
    }
}
