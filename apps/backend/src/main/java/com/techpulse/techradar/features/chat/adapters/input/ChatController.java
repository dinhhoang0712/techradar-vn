package com.techpulse.techradar.features.chat.adapters.input;

import com.techpulse.techradar.features.chat.application.ChatUseCase;
import com.techpulse.techradar.features.chat.adapters.input.dto.ChatMessageItem;
import com.techpulse.techradar.features.chat.adapters.input.dto.ChatMessageRequest;
import com.techpulse.techradar.features.chat.adapters.input.dto.ChatRequest;
import com.techpulse.techradar.features.chat.adapters.input.dto.ChatResponse;
import com.techpulse.techradar.features.chat.adapters.input.dto.ChatHealthResponse;
import com.techpulse.techradar.features.chat.adapters.input.dto.ChatSessionItem;
import com.techpulse.techradar.features.chat.adapters.input.dto.CreateSessionResponse;
import com.techpulse.techradar.shared.dto.ApiResponse;
import com.techpulse.techradar.shared.security.SecurityUtils;
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
    public Mono<ResponseEntity<ApiResponse<CreateSessionResponse>>> createSession() {
        return SecurityUtils.currentUserId()
                .flatMap(chatUseCase::createSession)
                .map(response -> ResponseEntity.ok(ApiResponse.success(response, "Session created")));
    }

    @Operation(summary = "List the current user's chat sessions")
    @GetMapping("/sessions")
    public Mono<ResponseEntity<ApiResponse<List<ChatSessionItem>>>> listSessions() {
        return SecurityUtils.currentUserId()
                .flatMapMany(chatUseCase::listSessions)
                .collectList()
                .map(sessions -> ResponseEntity.ok(ApiResponse.success(sessions, "Sessions retrieved")));
    }

    @Operation(summary = "Delete a chat session (and its messages)")
    @DeleteMapping("/session/{sessionId}")
    public Mono<ResponseEntity<ApiResponse<Void>>> deleteSession(@PathVariable String sessionId) {
        return SecurityUtils.currentUserId()
                .flatMap(userId -> chatUseCase.deleteSession(sessionId, userId))
                .thenReturn(ResponseEntity.ok(ApiResponse.<Void>success(null, "Session deleted")));
    }

    @Operation(summary = "List chat messages for a specific session")
    @GetMapping("/session/{sessionId}/messages")
    public Mono<ResponseEntity<ApiResponse<List<ChatMessageItem>>>> listMessages(
            @PathVariable String sessionId
    ) {
        return SecurityUtils.currentUserId()
                .flatMapMany(userId -> chatUseCase.listMessages(sessionId, userId))
                .collectList()
                .map(messages -> ResponseEntity.ok(ApiResponse.success(messages, "Message history retrieved")));
    }

    @Operation(summary = "Send a chat message and receive a full non-stream response")
    @PostMapping("/session/{sessionId}/messages")
    public Mono<ResponseEntity<ApiResponse<ChatResponse>>> postMessage(
            @PathVariable String sessionId,
            @Valid @RequestBody ChatMessageRequest request
    ) {
        return SecurityUtils.currentUserId()
                .flatMap(userId -> chatUseCase.chat(new ChatRequest(request.getQuery(), sessionId, userId)))
                .map(response -> ResponseEntity.ok(ApiResponse.success(response, "Chat response returned")));
    }

    @Operation(summary = "Send a chat message and stream the RAG answer via SSE")
    @PostMapping(value = "/session/{sessionId}/messages/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> postMessageStream(
            @PathVariable String sessionId,
            @Valid @RequestBody ChatMessageRequest request
    ) {
        return SecurityUtils.currentUserId()
                .flatMapMany(userId -> chatUseCase.streamChat(new ChatRequest(request.getQuery(), sessionId, userId)));
    }
}
