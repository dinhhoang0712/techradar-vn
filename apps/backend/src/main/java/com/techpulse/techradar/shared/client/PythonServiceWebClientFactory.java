package com.techpulse.techradar.shared.client;

import org.springframework.web.reactive.function.client.WebClient;

/**
 * Builds a {@link WebClient} for a Python microservice (ai-rag-core, ml-clustering, ...) from the
 * shared, injected {@link WebClient.Builder}, wiring the service base URL and — when configured —
 * the {@code X-Internal-Auth} header used to authenticate backend-to-backend calls.
 * <p>
 * This wiring used to be copy-pasted in every {@code Python*Client} adapter; centralizing it here
 * means a future change (e.g. rotating to a signed token) only needs one edit instead of one per
 * adapter.
 */
public final class PythonServiceWebClientFactory {

    private PythonServiceWebClientFactory() {
    }

    /**
     * @param webClientBuilder the shared builder injected by Spring (never mutated in place)
     * @param baseUrl          base URL of the target Python service
     * @param internalToken    shared secret sent as {@code X-Internal-Auth}, or blank/{@code null} to omit it
     */
    public static WebClient build(WebClient.Builder webClientBuilder, String baseUrl, String internalToken) {
        WebClient.Builder builder = webClientBuilder.baseUrl(baseUrl);
        if (internalToken != null && !internalToken.isBlank()) {
            builder = builder.defaultHeader("X-Internal-Auth", internalToken);
        }
        return builder.build();
    }
}
