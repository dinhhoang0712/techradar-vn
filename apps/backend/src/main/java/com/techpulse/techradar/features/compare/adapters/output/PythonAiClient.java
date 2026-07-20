package com.techpulse.techradar.features.compare.adapters.output;

import com.techpulse.techradar.features.compare.domain.TechComparison;
import com.techpulse.techradar.features.compare.ports.LlmSummaryPort;
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
 * Python AI client adapter for LLM summary generation.
 * Communicates with Python FastAPI service via HTTP.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PythonAiClient extends AbstractPythonServiceClient implements LlmSummaryPort {

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
    public Mono<String> generateSummary(TechComparison comparison) {
        Map<String, Object> body = new HashMap<>();
        body.put("tech1", comparison.getTechnology1());
        body.put("tech2", comparison.getTechnology2());
        body.put("growth_rate_1", comparison.getGrowthRate1());
        body.put("growth_rate_2", comparison.getGrowthRate2());
        body.put("job_count_1", comparison.getJobCount1());
        body.put("job_count_2", comparison.getJobCount2());
        body.put("article_count_1", comparison.getArticleCount1());
        body.put("article_count_2", comparison.getArticleCount2());

        Mono<String> request = client()
                .post()
                .uri("/internal/ai/llm-summary")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> (String) response.get("summary"));

        return mapMono(request, true, Duration.ofMillis(timeout),
                ex -> log.error("Failed to generate LLM summary from Python AI service", ex),
                ex -> "AI service unavailable: " + ex.getMessage());
    }
}
