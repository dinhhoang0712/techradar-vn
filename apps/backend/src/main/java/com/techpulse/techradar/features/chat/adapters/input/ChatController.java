package com.techpulse.techradar.features.chat.adapters.input;

import com.techpulse.techradar.features.chat.application.ChatUseCase;
import com.techpulse.techradar.features.chat.adapters.input.dto.ChatMessageItem;
import com.techpulse.techradar.features.chat.adapters.input.dto.ChatMessageRequest;
import com.techpulse.techradar.features.chat.adapters.input.dto.ChatRequest;
import com.techpulse.techradar.features.chat.adapters.input.dto.ChatResponse;
import com.techpulse.techradar.features.chat.adapters.input.dto.ChatHealthResponse;
import com.techpulse.techradar.features.chat.adapters.input.dto.CreateSessionResponse;
import com.techpulse.techradar.shared.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import jakarta.validation.Valid;

import java.util.List;

/**
 * Chat controller proxying chat requests to Python RAG service.
 */
@Tag(name = "Chat", description = "Chat / RAG endpoints")
@RestController
@RequestMapping("/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatUseCase chatUseCase;

    @Operation(summary = "Health check for chat / RAG service")
    @GetMapping
    public Mono<ResponseEntity<ApiResponse<ChatHealthResponse>>> health() {
        return chatUseCase.getHealth()
                .map(health -> ResponseEntity.ok(ApiResponse.success(health, "Health check passed")));
    }

    @Operation(summary = "Create a new chat session")
    @PostMapping("/session")
    public Mono<ResponseEntity<ApiResponse<CreateSessionResponse>>> createSession(
            @RequestHeader(value = "X-User-Id", required = false) String userId
    ) {
        return chatUseCase.createSession(userId)
                .map(response -> ResponseEntity.ok(ApiResponse.success(response, "Session created")));
    }

    @Operation(summary = "List chat messages for a specific session")
    @GetMapping("/session/{sessionId}/messages")
    public Mono<ResponseEntity<ApiResponse<List<ChatMessageItem>>>> listMessages(
            @PathVariable String sessionId
    ) {
        return chatUseCase.listMessages(sessionId)
                .collectList()
                .map(messages -> ResponseEntity.ok(ApiResponse.success(messages, "Message history retrieved")));
    }

    @Operation(summary = "Send a chat message and receive a full non-stream response")
    @PostMapping("/session/{sessionId}/messages")
    public Mono<ResponseEntity<ApiResponse<ChatResponse>>> postMessage(
            @PathVariable String sessionId,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @Valid @RequestBody ChatMessageRequest request
    ) {
        ChatRequest chatRequest = new ChatRequest(request.getQuery(), sessionId, userId);
        return chatUseCase.chat(chatRequest)
                .map(response -> ResponseEntity.ok(ApiResponse.success(response, "Chat response returned")));
    }

    @Operation(summary = "Send a chat message and stream the RAG answer via SSE")
    @PostMapping(value = "/session/{sessionId}/messages/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> postMessageStream(
            @PathVariable String sessionId,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @Valid @RequestBody ChatMessageRequest request
    ) {
        ChatRequest chatRequest = new ChatRequest(request.getQuery(), sessionId, userId);
        return chatUseCase.streamChat(chatRequest);
    }
}
