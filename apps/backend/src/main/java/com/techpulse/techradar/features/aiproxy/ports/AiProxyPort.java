package com.techpulse.techradar.features.aiproxy.ports;

import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;

/**
 * Shared gateway to the Python ai-rag-core service for the thin, single-call
 * AI endpoints (agent, career, recommend, report, summarize, forecast, interview).
 */
public interface AiProxyPort {

    Duration DEFAULT_TIMEOUT = Duration.ofMillis(60_000);

    Mono<Map<String, Object>> forward(String path, Map<String, Object> body, Duration timeout);
}
