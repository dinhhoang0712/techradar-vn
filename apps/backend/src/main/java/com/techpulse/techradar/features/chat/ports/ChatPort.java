package com.techpulse.techradar.features.chat.ports;

import com.techpulse.techradar.features.chat.adapters.input.dto.ChatHealthResponse;
import com.techpulse.techradar.features.chat.adapters.input.dto.ChatMessageItem;
import com.techpulse.techradar.features.chat.adapters.input.dto.ChatRequest;
import com.techpulse.techradar.features.chat.adapters.input.dto.ChatResponse;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ChatPort {

    Mono<ChatHealthResponse> getHealth();

    Mono<ChatResponse> chat(ChatRequest request);

    Flux<ChatMessageItem> listMessages(String sessionId);

    Flux<ServerSentEvent<String>> streamChat(ChatRequest request);
}
