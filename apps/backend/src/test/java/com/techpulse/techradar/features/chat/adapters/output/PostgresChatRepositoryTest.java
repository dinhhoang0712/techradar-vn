package com.techpulse.techradar.features.chat.adapters.output;

import com.techpulse.techradar.features.chat.domain.ChatMessage;
import com.techpulse.techradar.features.chat.domain.ChatMessageItem;
import com.techpulse.techradar.features.chat.domain.ChatSession;
import com.techpulse.techradar.features.chat.domain.ChatSessionItem;
import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.r2dbc.core.FetchSpec;
import org.springframework.r2dbc.core.RowsFetchSpec;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the SQL text, bind order, and row-mapping for {@link PostgresChatRepository}. Special
 * attention to {@code saveSession}/{@code saveMessage}: this repository previously regressed by
 * calling {@code .bind(name, null)} (which R2DBC rejects at runtime) for nullable
 * title/model_used/system_prompt/finish_reason columns instead of {@code .bindNull(...)} — the
 * null-field tests below simulate that real contract (a stubbed throw on {@code bind(name,
 * null)}) so they fail again if the bug is reintroduced.
 */
@ExtendWith(MockitoExtension.class)
class PostgresChatRepositoryTest {

    @Mock
    private DatabaseClient dbClient;
    @Mock
    private DatabaseClient.GenericExecuteSpec executeSpec;
    @Mock
    private RowsFetchSpec<ChatSession> sessionRowsFetchSpec;
    @Mock
    private RowsFetchSpec<ChatMessageItem> messageItemRowsFetchSpec;
    @Mock
    private RowsFetchSpec<ChatSessionItem> sessionItemRowsFetchSpec;
    @Mock
    private FetchSpec<Map<String, Object>> fetchSpec;
    @Mock
    private Row row;
    @Mock
    private RowMetadata rowMetadata;

    private PostgresChatRepository repository;

    private static final UUID SESSION_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @BeforeEach
    void setUp() {
        repository = new PostgresChatRepository(dbClient);
    }

    private void allowAnyNonNullBind() {
        when(executeSpec.bind(anyString(), any())).thenReturn(executeSpec);
    }

    private void trapNullBind(String columnName) {
        // Real R2DBC throws on .bind(name, null) — simulate it so a regression to bind() instead
        // of bindNull() for this column fails the test instead of silently passing. lenient()
        // because correct code never triggers this stub at all.
        lenient().when(executeSpec.bind(eq(columnName), isNull()))
                .thenThrow(new IllegalArgumentException("bind value must not be null: " + columnName));
    }

    @Test
    void saveSession_withAllFieldsPresent_insertsAndUpsertsOnConflict() {
        ChatSession session = ChatSession.builder().id(SESSION_ID).userId(USER_ID)
                .title("My chat").modelUsed("gpt-4").systemPrompt("Be helpful").build();

        when(dbClient.sql(
                "INSERT INTO chat_session (id, user_id, title, model_used, system_prompt, created_at, updated_at) " +
                        "VALUES (:id, :user_id, :title, :model_used, :system_prompt, :created_at, :updated_at) " +
                        "ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, model_used = EXCLUDED.model_used, " +
                        "system_prompt = EXCLUDED.system_prompt, updated_at = EXCLUDED.updated_at"))
                .thenReturn(executeSpec);
        allowAnyNonNullBind();
        when(executeSpec.fetch()).thenReturn(fetchSpec);
        when(fetchSpec.rowsUpdated()).thenReturn(Mono.just(1L));

        StepVerifier.create(repository.saveSession(session))
                .assertNext(saved -> {
                    assertThat(saved.getId()).isEqualTo(SESSION_ID);
                    assertThat(saved.getCreatedAt()).isNotNull();
                    assertThat(saved.getUpdatedAt()).isNotNull();
                })
                .verifyComplete();

        verify(executeSpec).bind("id", SESSION_ID);
        verify(executeSpec).bind("user_id", USER_ID);
        verify(executeSpec).bind("title", "My chat");
        verify(executeSpec).bind("model_used", "gpt-4");
        verify(executeSpec).bind("system_prompt", "Be helpful");
    }

    @Test
    void saveSession_generatesIdWhenMissing() {
        ChatSession session = ChatSession.builder().userId(USER_ID).title("t").build();

        when(dbClient.sql(anyString())).thenReturn(executeSpec);
        allowAnyNonNullBind();
        // session.modelUsed/systemPrompt are null here, so bindNullable falls through to
        // bindNull() for them - must be stubbed or the null-returning chain NPEs.
        when(executeSpec.bindNull(anyString(), any())).thenReturn(executeSpec);
        when(executeSpec.fetch()).thenReturn(fetchSpec);
        when(fetchSpec.rowsUpdated()).thenReturn(Mono.just(1L));

        StepVerifier.create(repository.saveSession(session))
                .assertNext(saved -> assertThat(saved.getId()).isNotNull())
                .verifyComplete();
    }

    @Test
    void saveSession_withNullTitleModelAndSystemPrompt_usesBindNull_notBind() {
        ChatSession session = ChatSession.builder().id(SESSION_ID).userId(USER_ID)
                .title(null).modelUsed(null).systemPrompt(null).build();

        when(dbClient.sql(anyString())).thenReturn(executeSpec);
        allowAnyNonNullBind();
        trapNullBind("title");
        trapNullBind("model_used");
        trapNullBind("system_prompt");
        when(executeSpec.bindNull("title", String.class)).thenReturn(executeSpec);
        when(executeSpec.bindNull("model_used", String.class)).thenReturn(executeSpec);
        when(executeSpec.bindNull("system_prompt", String.class)).thenReturn(executeSpec);
        when(executeSpec.fetch()).thenReturn(fetchSpec);
        when(fetchSpec.rowsUpdated()).thenReturn(Mono.just(1L));

        StepVerifier.create(repository.saveSession(session))
                .assertNext(saved -> assertThat(saved.getTitle()).isNull())
                .verifyComplete();

        verify(executeSpec).bindNull("title", String.class);
        verify(executeSpec).bindNull("model_used", String.class);
        verify(executeSpec).bindNull("system_prompt", String.class);
    }

    @Test
    void deleteSession_bindsIdAndReturnsRowsUpdated() {
        String sessionId = SESSION_ID.toString();
        when(dbClient.sql("DELETE FROM chat_session WHERE id = :id")).thenReturn(executeSpec);
        when(executeSpec.bind("id", SESSION_ID)).thenReturn(executeSpec);
        when(executeSpec.fetch()).thenReturn(fetchSpec);
        when(fetchSpec.rowsUpdated()).thenReturn(Mono.just(1L));

        StepVerifier.create(repository.deleteSession(sessionId))
                .expectNext(1L)
                .verifyComplete();
    }

    @Test
    @SuppressWarnings("unchecked")
    void findSessionById_bindsIdAndMapsRow() {
        when(dbClient.sql(
                "SELECT id, user_id, title, model_used, system_prompt, created_at, updated_at " +
                        "FROM chat_session WHERE id = :id"))
                .thenReturn(executeSpec);
        when(executeSpec.bind("id", SESSION_ID)).thenReturn(executeSpec);
        when(executeSpec.map(any(BiFunction.class))).thenReturn(sessionRowsFetchSpec);
        when(sessionRowsFetchSpec.one()).thenReturn(Mono.just(ChatSession.builder().id(SESSION_ID).build()));

        StepVerifier.create(repository.findSessionById(SESSION_ID.toString()))
                .assertNext(s -> assertThat(s.getId()).isEqualTo(SESSION_ID))
                .verifyComplete();
    }

    @Test
    @SuppressWarnings("unchecked")
    void findSessionById_rowMapper_mapsAllSevenColumns() {
        when(dbClient.sql(anyString())).thenReturn(executeSpec);
        when(executeSpec.bind(anyString(), any())).thenReturn(executeSpec);
        when(executeSpec.map(any(BiFunction.class))).thenReturn(sessionRowsFetchSpec);
        when(sessionRowsFetchSpec.one()).thenReturn(Mono.empty());
        Instant now = Instant.now();
        when(row.get("id", UUID.class)).thenReturn(SESSION_ID);
        when(row.get("user_id", UUID.class)).thenReturn(USER_ID);
        when(row.get("title", String.class)).thenReturn("My chat");
        when(row.get("model_used", String.class)).thenReturn("gpt-4");
        when(row.get("system_prompt", String.class)).thenReturn("Be helpful");
        when(row.get("created_at", Instant.class)).thenReturn(now);
        when(row.get("updated_at", Instant.class)).thenReturn(now);

        repository.findSessionById(SESSION_ID.toString()).subscribe();

        ArgumentCaptor<BiFunction<Row, RowMetadata, ChatSession>> captor = ArgumentCaptor.forClass(BiFunction.class);
        verify(executeSpec).map(captor.capture());
        ChatSession mapped = captor.getValue().apply(row, rowMetadata);

        assertThat(mapped.getId()).isEqualTo(SESSION_ID);
        assertThat(mapped.getUserId()).isEqualTo(USER_ID);
        assertThat(mapped.getTitle()).isEqualTo("My chat");
        assertThat(mapped.getModelUsed()).isEqualTo("gpt-4");
        assertThat(mapped.getSystemPrompt()).isEqualTo("Be helpful");
        assertThat(mapped.getCreatedAt()).isEqualTo(now);
        assertThat(mapped.getUpdatedAt()).isEqualTo(now);
    }

    @Test
    void saveMessage_withFinishReasonPresent_insertsAllColumns() {
        UUID messageId = UUID.randomUUID();
        ChatMessage message = ChatMessage.builder().id(messageId).sessionId(SESSION_ID).role("assistant")
                .content("Hello").promptTokens(10).completionTokens(20).finishReason("stop").build();

        when(dbClient.sql(
                "INSERT INTO chat_message (id, session_id, role, content, prompt_tokens, completion_tokens, finish_reason, created_at) " +
                        "VALUES (:id, :session_id, :role, :content, :prompt_tokens, :completion_tokens, :finish_reason, :created_at)"))
                .thenReturn(executeSpec);
        allowAnyNonNullBind();
        when(executeSpec.fetch()).thenReturn(fetchSpec);
        when(fetchSpec.rowsUpdated()).thenReturn(Mono.just(1L));

        StepVerifier.create(repository.saveMessage(message))
                .assertNext(saved -> assertThat(saved.getCreatedAt()).isNotNull())
                .verifyComplete();

        verify(executeSpec).bind("id", messageId);
        verify(executeSpec).bind("session_id", SESSION_ID);
        verify(executeSpec).bind("role", "assistant");
        verify(executeSpec).bind("content", "Hello");
        verify(executeSpec).bind("prompt_tokens", 10);
        verify(executeSpec).bind("completion_tokens", 20);
        verify(executeSpec).bind("finish_reason", "stop");
    }

    @Test
    void saveMessage_withNullTokensAndFinishReason_defaultsTokensToZero_andBindNullsFinishReason() {
        ChatMessage message = ChatMessage.builder().sessionId(SESSION_ID).role("user").content("Hi").build();

        when(dbClient.sql(anyString())).thenReturn(executeSpec);
        allowAnyNonNullBind();
        trapNullBind("finish_reason");
        when(executeSpec.bindNull("finish_reason", String.class)).thenReturn(executeSpec);
        when(executeSpec.fetch()).thenReturn(fetchSpec);
        when(fetchSpec.rowsUpdated()).thenReturn(Mono.just(1L));

        StepVerifier.create(repository.saveMessage(message))
                .assertNext(saved -> assertThat(saved.getFinishReason()).isNull())
                .verifyComplete();

        verify(executeSpec).bind("prompt_tokens", 0);
        verify(executeSpec).bind("completion_tokens", 0);
        verify(executeSpec).bindNull("finish_reason", String.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void listMessages_bindsSessionIdOrderedByCreatedAt_andMapsEveryRow() {
        when(dbClient.sql(
                "SELECT id, role, content FROM chat_message " +
                        "WHERE session_id = :session_id ORDER BY created_at ASC"))
                .thenReturn(executeSpec);
        when(executeSpec.bind("session_id", SESSION_ID)).thenReturn(executeSpec);
        when(executeSpec.map(any(BiFunction.class))).thenReturn(messageItemRowsFetchSpec);
        when(messageItemRowsFetchSpec.all()).thenReturn(Flux.just(
                new ChatMessageItem(UUID.randomUUID(), "user", "hi")));

        StepVerifier.create(repository.listMessages(SESSION_ID.toString()))
                .expectNextMatches(item -> item.getRole().equals("user") && item.getContent().equals("hi"))
                .verifyComplete();
    }

    @Test
    @SuppressWarnings("unchecked")
    void listMessages_rowMapper_mapsThreeColumns() {
        when(dbClient.sql(anyString())).thenReturn(executeSpec);
        when(executeSpec.bind(anyString(), any())).thenReturn(executeSpec);
        when(executeSpec.map(any(BiFunction.class))).thenReturn(messageItemRowsFetchSpec);
        when(messageItemRowsFetchSpec.all()).thenReturn(Flux.empty());
        UUID messageId = UUID.randomUUID();
        when(row.get("id", UUID.class)).thenReturn(messageId);
        when(row.get("role", String.class)).thenReturn("assistant");
        when(row.get("content", String.class)).thenReturn("hello there");

        repository.listMessages(SESSION_ID.toString()).subscribe();

        ArgumentCaptor<BiFunction<Row, RowMetadata, ChatMessageItem>> captor = ArgumentCaptor.forClass(BiFunction.class);
        verify(executeSpec).map(captor.capture());
        ChatMessageItem mapped = captor.getValue().apply(row, rowMetadata);

        assertThat(mapped.getId()).isEqualTo(messageId);
        assertThat(mapped.getRole()).isEqualTo("assistant");
        assertThat(mapped.getContent()).isEqualTo("hello there");
    }

    @Test
    @SuppressWarnings("unchecked")
    void listSessionsByUser_bindsUserIdOrderedByCreatedAtDesc_andMapsEveryRow() {
        when(dbClient.sql(
                "SELECT id, title, created_at FROM chat_session " +
                        "WHERE user_id = :user_id ORDER BY created_at DESC"))
                .thenReturn(executeSpec);
        when(executeSpec.bind("user_id", USER_ID)).thenReturn(executeSpec);
        when(executeSpec.map(any(BiFunction.class))).thenReturn(sessionItemRowsFetchSpec);
        Instant now = Instant.now();
        when(sessionItemRowsFetchSpec.all()).thenReturn(Flux.just(new ChatSessionItem(SESSION_ID, "title", now)));

        StepVerifier.create(repository.listSessionsByUser(USER_ID.toString()))
                .expectNextMatches(item -> item.getSessionId().equals(SESSION_ID) && item.getTitle().equals("title"))
                .verifyComplete();
    }

    @Test
    @SuppressWarnings("unchecked")
    void listSessionsByUser_rowMapper_mapsThreeColumns() {
        when(dbClient.sql(anyString())).thenReturn(executeSpec);
        when(executeSpec.bind(anyString(), any())).thenReturn(executeSpec);
        when(executeSpec.map(any(BiFunction.class))).thenReturn(sessionItemRowsFetchSpec);
        when(sessionItemRowsFetchSpec.all()).thenReturn(Flux.empty());
        Instant now = Instant.now();
        when(row.get("id", UUID.class)).thenReturn(SESSION_ID);
        when(row.get("title", String.class)).thenReturn("My chat");
        when(row.get("created_at", Instant.class)).thenReturn(now);

        repository.listSessionsByUser(USER_ID.toString()).subscribe();

        ArgumentCaptor<BiFunction<Row, RowMetadata, ChatSessionItem>> captor = ArgumentCaptor.forClass(BiFunction.class);
        verify(executeSpec).map(captor.capture());
        ChatSessionItem mapped = captor.getValue().apply(row, rowMetadata);

        assertThat(mapped.getSessionId()).isEqualTo(SESSION_ID);
        assertThat(mapped.getTitle()).isEqualTo("My chat");
        assertThat(mapped.getCreatedAt()).isEqualTo(now);
    }
}
