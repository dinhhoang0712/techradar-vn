package com.techpulse.techradar.features.compare.adapters.output;

import com.techpulse.techradar.features.compare.domain.TechComparison;
import com.techpulse.techradar.features.compare.ports.LlmSummaryPort;
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
 * Python AI client adapter for LLM summary generation.
 * Communicates with Python FastAPI service via HTTP.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PythonAiClient implements LlmSummaryPort {

    private final WebClient.Builder webClientBuilder;

    @Value("${app.python.ai.base-url:http://localhost:8000}")
    private String pythonAiBaseUrl;

    @Value("${app.python.ai.timeout:60000}")
    private long timeout;

    @Override
    public Mono<String> generateSummary(TechComparison comparison) {
        Map<String, Object> request = new HashMap<>();
        request.put("tech1", comparison.getTechnology1());
        request.put("tech2", comparison.getTechnology2());
        request.put("growth_rate_1", comparison.getGrowthRate1());
        request.put("growth_rate_2", comparison.getGrowthRate2());
        request.put("job_count_1", comparison.getJobCount1());
        request.put("job_count_2", comparison.getJobCount2());
        request.put("article_count_1", comparison.getArticleCount1());
        request.put("article_count_2", comparison.getArticleCount2());

        return webClientBuilder.build()
                .post()
                .uri(pythonAiBaseUrl + "/internal/ai/llm-summary")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> (String) response.get("summary"))
                .retryWhen(Retry.backoff(3, Duration.ofSeconds(1)))
                .timeout(Duration.ofMillis(timeout))
                .onErrorResume(ex -> {
                    log.error("Failed to generate LLM summary from Python AI service", ex);
                    return Mono.error(
                            new DatabaseUnavailableException(
                                    "AI service unavailable: " + ex.getMessage()
                            )
                    );
                });
    }
}
