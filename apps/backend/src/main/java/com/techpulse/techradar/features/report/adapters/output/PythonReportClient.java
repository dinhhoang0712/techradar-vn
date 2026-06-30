package com.techpulse.techradar.features.report.adapters.output;

import com.techpulse.techradar.features.report.ports.ReportServicePort;
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
public class PythonReportClient implements ReportServicePort {

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
    public Mono<Map<String, Object>> generateReport(String period, int topN, String format) {
        Map<String, Object> body = Map.of(
                "period", period,
                "top_n", topN,
                "format", format
        );
        return webClient()
                .post()
                .uri("/report")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(MAP_TYPE)
                .timeout(Duration.ofMillis(timeout))
                .onErrorResume(ex -> {
                    log.error("Report service error for period={}", period, ex);
                    return Mono.error(new DatabaseUnavailableException("Report service unavailable"));
                });
    }
}
