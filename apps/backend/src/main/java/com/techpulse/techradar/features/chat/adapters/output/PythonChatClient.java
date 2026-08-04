package com.techpulse.techradar.features.chat.adapters.output;

import com.techpulse.techradar.features.chat.domain.ChatHealthResponse;
import com.techpulse.techradar.features.chat.domain.ChatMessageItem;
import com.techpulse.techradar.features.chat.domain.ChatRequest;
import com.techpulse.techradar.features.chat.domain.ChatResponse;
import com.techpulse.techradar.features.chat.ports.ChatPort;
import com.techpulse.techradar.shared.client.PythonServiceWebClientFactory;
import com.techpulse.techradar.shared.http.AbstractPythonServiceClient;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Python RAG client adapter for chat functionality.
 */
@Component
@RequiredArgsConstructor
public class PythonChatClient extends AbstractPythonServiceClient implements ChatPort {

    private final WebClient.Builder webClientBuilder;

    /** Shared with every other ai-rag-core client — see Resilience4jConfig. */
    @Qualifier("aiRagCoreCircuitBreaker")
    private final CircuitBreaker circuitBreaker;

    @Value("${app.python.rag.base-url:http://localhost:8000}")
    private String pythonRagBaseUrl;

    @Value("${app.python.rag.timeout:120000}")
    private long timeout;

    @Value("${app.python.internal-token:}")
    private String internalToken;

    private WebClient client() {
        return PythonServiceWebClientFactory.build(webClientBuilder, pythonRagBaseUrl, internalToken);
    }

    @Override
    public Mono<ChatHealthResponse> getHealth() {
        Mono<ChatHealthResponse> request = client()
                .get()
                .uri("/health")
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(ChatHealthResponse.class);
        return mapMono(request, circuitBreaker, false, Duration.ofMillis(timeout), "RAG service unavailable");
    }

    @Override
    public Mono<ChatResponse> chat(ChatRequest chatRequest) {
        Mono<ChatResponse> response = client()
                .post()
                .uri("/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(chatRequest)
                .retrieve()
                .bodyToMono(ChatResponse.class);
        return mapMono(response, circuitBreaker, false, Duration.ofMillis(timeout), "RAG service unavailable");
    }

    @Override
    public Flux<ChatMessageItem> listMessages(String sessionId) {
        Flux<ChatMessageItem> request = client()
                .get()
                .uri(uriBuilder -> uriBuilder.path("/chat/session/{sessionId}/messages").build(sessionId))
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToFlux(ChatMessageItem.class);
        return mapFlux(request, circuitBreaker, false, Duration.ofMillis(timeout), "RAG service unavailable");
    }

    @Override
    public Flux<ServerSentEvent<String>> streamChat(ChatRequest chatRequest) {
        Flux<ServerSentEvent<String>> stream = client()
                .post()
                .uri("/chat/stream")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue(chatRequest)
                .retrieve()
                .bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<String>>() {});
        return mapFlux(stream, circuitBreaker, false, Duration.ofMillis(timeout), "RAG stream unavailable");
    }
}
