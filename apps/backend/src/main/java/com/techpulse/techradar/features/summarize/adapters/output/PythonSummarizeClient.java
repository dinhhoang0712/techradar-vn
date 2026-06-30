package com.techpulse.techradar.features.summarize.adapters.output;

import com.techpulse.techradar.features.summarize.ports.SummarizeServicePort;
import com.techpulse.techradar.shared.exception.DatabaseUnavailableException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class PythonSummarizeClient implements SummarizeServicePort {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
            new ParameterizedTypeReference<>() {};

    private final WebClient.Builder webClientBuilder;

    @Value("${app.python.ai.base-url:http://localhost:8000}")
    private String aiBaseUrl;

    @Value("${app.python.ai.timeout:60000}")
    private long timeout;

    @Value("${app.python.internal-token:}")
    private String internalToken;

    private WebClient webClient() {
        WebClient.Builder builder = webClientBuilder.baseUrl(aiBaseUrl);
        if (internalToken != null && !internalToken.isBlank()) {
            builder = builder.defaultHeader("X-Internal-Auth", internalToken);
        }
        return builder.build();
    }

    @Override
    public Mono<Map<String, Object>> summarize(Map<String, Object> request) {
        return webClient()
                .post()
                .uri("/summarize")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(MAP_TYPE)
                .timeout(Duration.ofMillis(timeout))
                .onErrorResume(ex -> {
                    log.error("Summarize service error", ex);
                    return Mono.error(new DatabaseUnavailableException("Summarize service unavailable"));
                });
    }
}
