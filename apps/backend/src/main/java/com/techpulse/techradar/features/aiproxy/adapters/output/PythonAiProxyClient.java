package com.techpulse.techradar.features.aiproxy.adapters.output;

import com.techpulse.techradar.features.aiproxy.ports.AiProxyPort;
import com.techpulse.techradar.shared.client.PythonServiceWebClientFactory;
import com.techpulse.techradar.shared.http.AbstractPythonServiceClient;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
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
public class PythonAiProxyClient extends AbstractPythonServiceClient implements AiProxyPort {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
            new ParameterizedTypeReference<>() {};

    private final WebClient.Builder webClientBuilder;

    /** Shared with every other ai-rag-core client — see Resilience4jConfig. */
    @Qualifier("aiRagCoreCircuitBreaker")
    private final CircuitBreaker circuitBreaker;

    @Value("${app.python.ai.base-url:http://localhost:8000}")
    private String aiBaseUrl;

    @Value("${app.python.internal-token:}")
    private String internalToken;

    private WebClient client() {
        return PythonServiceWebClientFactory.build(webClientBuilder, aiBaseUrl, internalToken);
    }

    @Override
    public Mono<Map<String, Object>> forward(String path, Map<String, Object> body, Duration timeout) {
        Mono<Map<String, Object>> request = client()
                .post()
                .uri(path)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(MAP_TYPE);
        return mapMono(request, circuitBreaker, false, timeout,
                ex -> log.error("AI proxy service error for path={}", path, ex),
                "AI service unavailable: " + path);
    }
}
