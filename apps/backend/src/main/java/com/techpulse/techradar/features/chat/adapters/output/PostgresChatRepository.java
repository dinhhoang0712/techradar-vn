package com.techpulse.techradar.features.chat.adapters.output;

import com.techpulse.techradar.features.chat.domain.ChatMessage;
import com.techpulse.techradar.features.chat.domain.ChatSession;
import com.techpulse.techradar.features.chat.adapters.input.dto.ChatMessageItem;
import com.techpulse.techradar.features.chat.adapters.input.dto.ChatSessionItem;
import com.techpulse.techradar.features.chat.ports.ChatRepository;
import io.r2dbc.spi.Row;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

/**
 * PostgreSQL adapter for chat session and message persistence.
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class PostgresChatRepository implements ChatRepository {

    private final DatabaseClient dbClient;

    @Override
    public Mono<ChatSession> saveSession(ChatSession session) {
        if (session.getId() == null) {
            session.setId(UUID.randomUUID());
        }

        Instant now = Instant.now();
        session.setCreatedAt(session.getCreatedAt() != null ? session.getCreatedAt() : now);
        session.setUpdatedAt(now);

        DatabaseClient.GenericExecuteSpec spec = dbClient.sql(
                "INSERT INTO chat_session (id, user_id, title, model_used, system_prompt, created_at, updated_at) " +
                        "VALUES (:id, :user_id, :title, :model_used, :system_prompt, :created_at, :updated_at) " +
                        "ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, model_used = EXCLUDED.model_used, " +
                        "system_prompt = EXCLUDED.system_prompt, updated_at = EXCLUDED.updated_at"
        )
                .bind("id", session.getId())
                .bind("user_id", session.getUserId())
                .bind("created_at", session.getCreatedAt())
                .bind("updated_at", session.getUpdatedAt());
        spec = bindNullable(spec, "title", session.getTitle());
        spec = bindNullable(spec, "model_used", session.getModelUsed());
        spec = bindNullable(spec, "system_prompt", session.getSystemPrompt());

        return spec.fetch().rowsUpdated().thenReturn(session)
                .doOnSuccess(s -> log.debug("Saved chat session id={} user_id={}", s.getId(), s.getUserId()));
    }

    @Override
    public Mono<Long> deleteSession(String sessionId) {
        return dbClient.sql("DELETE FROM chat_session WHERE id = :id")
                .bind("id", UUID.fromString(sessionId))
                .fetch()
                .rowsUpdated()
                .doOnNext(rows -> log.info("Deleted chat session id={} (rows={})", sessionId, rows));
    }

    private static DatabaseClient.GenericExecuteSpec bindNullable(
            DatabaseClient.GenericExecuteSpec spec, String name, String value) {
        return value != null ? spec.bind(name, value) : spec.bindNull(name, String.class);
    }

    @Override
    public Mono<ChatSession> findSessionById(String sessionId) {
        return dbClient.sql(
                "SELECT id, user_id, title, model_used, system_prompt, created_at, updated_at " +
                        "FROM chat_session WHERE id = :id"
        )
                .bind("id", UUID.fromString(sessionId))
                .map((row, meta) -> mapRowToSession(row))
                .one();
    }

    @Override
    public Mono<ChatMessage> saveMessage(ChatMessage message) {
        if (message.getId() == null) {
            message.setId(UUID.randomUUID());
        }

        message.setCreatedAt(message.getCreatedAt() != null ? message.getCreatedAt() : Instant.now());

        DatabaseClient.GenericExecuteSpec spec = dbClient.sql(
                "INSERT INTO chat_message (id, session_id, role, content, prompt_tokens, completion_tokens, finish_reason, created_at) " +
                        "VALUES (:id, :session_id, :role, :content, :prompt_tokens, :completion_tokens, :finish_reason, :created_at)"
        )
                .bind("id", message.getId())
                .bind("session_id", message.getSessionId())
                .bind("role", message.getRole())
                .bind("content", message.getContent())
                .bind("prompt_tokens", message.getPromptTokens() != null ? message.getPromptTokens() : 0)
                .bind("completion_tokens", message.getCompletionTokens() != null ? message.getCompletionTokens() : 0)
                .bind("created_at", message.getCreatedAt());
        spec = bindNullable(spec, "finish_reason", message.getFinishReason());

        return spec.fetch().rowsUpdated().thenReturn(message)
                .doOnSuccess(m -> log.debug("Saved chat message id={} session_id={} role={}",
                        m.getId(), m.getSessionId(), m.getRole()));
    }

    @Override
    public Flux<ChatMessageItem> listMessages(String sessionId) {
        return dbClient.sql(
                "SELECT id, role, content FROM chat_message " +
                        "WHERE session_id = :session_id ORDER BY created_at ASC"
        )
                .bind("session_id", UUID.fromString(sessionId))
                .map((row, meta) -> mapRowToMessageItem(row))
                .all();
    }

    @Override
    public Flux<ChatSessionItem> listSessionsByUser(String userId) {
        return dbClient.sql(
                "SELECT id, title, created_at FROM chat_session " +
                        "WHERE user_id = :user_id ORDER BY created_at DESC"
        )
                .bind("user_id", UUID.fromString(userId))
                .map((row, meta) -> new ChatSessionItem(
                        row.get("id", UUID.class),
                        row.get("title", String.class),
                        row.get("created_at", Instant.class)))
                .all();
    }

    private ChatSession mapRowToSession(Row row) {
        return ChatSession.builder()
                .id(row.get("id", UUID.class))
                .userId(row.get("user_id", UUID.class))
                .title(row.get("title", String.class))
                .modelUsed(row.get("model_used", String.class))
                .systemPrompt(row.get("system_prompt", String.class))
                .createdAt(row.get("created_at", Instant.class))
                .updatedAt(row.get("updated_at", Instant.class))
                .build();
    }

    private ChatMessageItem mapRowToMessageItem(Row row) {
        return new ChatMessageItem(
                row.get("id", UUID.class),
                row.get("role", String.class),
                row.get("content", String.class)
        );
    }
}
