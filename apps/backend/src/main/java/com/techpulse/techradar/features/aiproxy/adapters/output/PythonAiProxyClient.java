package com.techpulse.techradar.features.aiproxy.adapters.output;

import com.techpulse.techradar.features.aiproxy.ports.AiProxyPort;
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
public class PythonAiProxyClient implements AiProxyPort {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
            new ParameterizedTypeReference<>() {};

    private final WebClient.Builder webClientBuilder;

    @Value("${app.python.ai.base-url:http://localhost:8000}")
    private String aiBaseUrl;

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
    public Mono<Map<String, Object>> forward(String path, Map<String, Object> body, Duration timeout) {
        return webClient()
                .post()
                .uri(path)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(MAP_TYPE)
                .timeout(timeout)
                .onErrorResume(ex -> {
                    log.error("AI proxy service error for path={}", path, ex);
                    return Mono.error(new DatabaseUnavailableException("AI service unavailable: " + path));
                });
    }
}
