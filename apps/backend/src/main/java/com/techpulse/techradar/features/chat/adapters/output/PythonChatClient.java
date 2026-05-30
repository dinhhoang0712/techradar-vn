package com.techpulse.techradar.features.chat.adapters.output;

import com.techpulse.techradar.features.chat.adapters.input.dto.ChatHealthResponse;
import com.techpulse.techradar.features.chat.adapters.input.dto.ChatMessageItem;
import com.techpulse.techradar.features.chat.adapters.input.dto.ChatRequest;
import com.techpulse.techradar.features.chat.adapters.input.dto.ChatResponse;
import com.techpulse.techradar.features.chat.ports.ChatPort;
import com.techpulse.techradar.shared.exception.DatabaseUnavailableException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.core.ParameterizedTypeReference;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Python RAG client adapter for chat functionality.
 */
@Component
@RequiredArgsConstructor
public class PythonChatClient implements ChatPort {

    private final WebClient.Builder webClientBuilder;

    @Value("${app.python.rag.base-url:http://localhost:8000}")
    private String pythonRagBaseUrl;

    @Value("${app.python.rag.timeout:120000}")
    private long timeout;

    private WebClient webClient() {
        return webClientBuilder
                .baseUrl(pythonRagBaseUrl)
                .build();
    }

    @Override
    public Mono<ChatHealthResponse> getHealth() {
        return webClient()
                .get()
                .uri("/health")
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(ChatHealthResponse.class)
                .timeout(Duration.ofMillis(timeout))
                .onErrorMap(ex -> new DatabaseUnavailableException("RAG service unavailable", ex));
    }

    @Override
    public Mono<ChatResponse> chat(ChatRequest request) {
        return webClient()
                .post()
                .uri("/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(ChatResponse.class)
                .timeout(Duration.ofMillis(timeout))
                .onErrorMap(ex -> new DatabaseUnavailableException("RAG service unavailable", ex));
    }

    @Override
    public Flux<ChatMessageItem> listMessages(String sessionId) {
        return webClient()
                .get()
                .uri(uriBuilder -> uriBuilder.path("/chat/session/{sessionId}/messages").build(sessionId))
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToFlux(ChatMessageItem.class)
                .timeout(Duration.ofMillis(timeout))
                .onErrorMap(ex -> new DatabaseUnavailableException("RAG service unavailable", ex));
    }

    @Override
    public Flux<ServerSentEvent<String>> streamChat(ChatRequest request) {
        return webClient()
                .post()
                .uri("/chat/stream")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue(request)
                .retrieve()
                .bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<String>>() {})
                .timeout(Duration.ofMillis(timeout))
                .onErrorMap(ex -> new DatabaseUnavailableException("RAG stream unavailable", ex));
    }
}
