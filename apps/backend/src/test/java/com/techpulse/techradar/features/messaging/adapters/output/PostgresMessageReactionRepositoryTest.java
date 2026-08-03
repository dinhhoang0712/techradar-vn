package com.techpulse.techradar.features.messaging.adapters.output;

import com.techpulse.techradar.features.messaging.ports.MessageReactionRepository.ReactionRow;
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

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostgresMessageReactionRepositoryTest {

    @Mock
    private DatabaseClient dbClient;
    @Mock
    private DatabaseClient.GenericExecuteSpec executeSpec;
    @Mock
    private RowsFetchSpec<ReactionRow> rowsFetchSpec;
    @Mock
    private FetchSpec<Map<String, Object>> fetchSpec;
    @Mock
    private Row row;
    @Mock
    private RowMetadata rowMetadata;

    private PostgresMessageReactionRepository repository;

    @BeforeEach
    void setUp() {
        repository = new PostgresMessageReactionRepository(dbClient);
    }

    @Test
    void upsert_bindsMessageIdUserIdAndEmoji_andCompletes() {
        UUID messageId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(dbClient.sql(
                "INSERT INTO message_reaction (message_id, user_id, emoji, created_at) " +
                        "VALUES (:message_id, :user_id, :emoji, now()) " +
                        "ON CONFLICT (message_id, user_id) DO UPDATE SET emoji = EXCLUDED.emoji, created_at = EXCLUDED.created_at"))
                .thenReturn(executeSpec);
        when(executeSpec.bind(anyString(), any())).thenReturn(executeSpec);
        when(executeSpec.fetch()).thenReturn(fetchSpec);
        when(fetchSpec.rowsUpdated()).thenReturn(Mono.just(1L));

        StepVerifier.create(repository.upsert(messageId, userId, "👍")).verifyComplete();

        verify(executeSpec).bind("message_id", messageId);
        verify(executeSpec).bind("user_id", userId);
        verify(executeSpec).bind("emoji", "👍");
    }

    @Test
    void remove_bindsMessageIdAndUserId_andCompletes() {
        UUID messageId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(dbClient.sql("DELETE FROM message_reaction WHERE message_id = :message_id AND user_id = :user_id"))
                .thenReturn(executeSpec);
        when(executeSpec.bind(anyString(), any())).thenReturn(executeSpec);
        when(executeSpec.fetch()).thenReturn(fetchSpec);
        when(fetchSpec.rowsUpdated()).thenReturn(Mono.just(1L));

        StepVerifier.create(repository.remove(messageId, userId)).verifyComplete();

        verify(executeSpec).bind("message_id", messageId);
        verify(executeSpec).bind("user_id", userId);
    }

    @Test
    @SuppressWarnings("unchecked")
    void findByMessageId_bindsMessageId_andMapsRows() {
        UUID messageId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(dbClient.sql("SELECT message_id, user_id, emoji FROM message_reaction WHERE message_id = :message_id"))
                .thenReturn(executeSpec);
        when(executeSpec.bind(anyString(), any())).thenReturn(executeSpec);
        when(executeSpec.map(any(BiFunction.class))).thenReturn(rowsFetchSpec);
        when(row.get("message_id", UUID.class)).thenReturn(messageId);
        when(row.get("user_id", UUID.class)).thenReturn(userId);
        when(row.get("emoji", String.class)).thenReturn("❤️");
        ReactionRow expected = new ReactionRow(messageId, userId, "❤️");
        when(rowsFetchSpec.all()).thenReturn(Flux.just(expected));

        StepVerifier.create(repository.findByMessageId(messageId)).expectNext(expected).verifyComplete();

        verify(executeSpec).bind("message_id", messageId);

        ArgumentCaptor<BiFunction<Row, RowMetadata, ReactionRow>> captor = ArgumentCaptor.forClass(BiFunction.class);
        verify(executeSpec).map(captor.capture());
        ReactionRow mapped = captor.getValue().apply(row, rowMetadata);
        assertThat(mapped).isEqualTo(expected);
    }

    @Test
    void findByMessageIds_returnsEmptyWithoutQueryingWhenGivenNoIds() {
        StepVerifier.create(repository.findByMessageIds(List.of())).verifyComplete();

        verify(dbClient, never()).sql(anyString());
    }

    @Test
    @SuppressWarnings("unchecked")
    void findByMessageIds_bindsAnArrayOfIds() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();

        when(dbClient.sql("SELECT message_id, user_id, emoji FROM message_reaction WHERE message_id = ANY(:message_ids)"))
                .thenReturn(executeSpec);
        when(executeSpec.bind(anyString(), any())).thenReturn(executeSpec);
        when(executeSpec.map(any(BiFunction.class))).thenReturn(rowsFetchSpec);
        when(rowsFetchSpec.all()).thenReturn(Flux.empty());

        StepVerifier.create(repository.findByMessageIds(List.of(id1, id2))).verifyComplete();

        ArgumentCaptor<UUID[]> captor = ArgumentCaptor.forClass(UUID[].class);
        verify(executeSpec).bind(anyString(), captor.capture());
        assertThat(captor.getValue()).containsExactlyInAnyOrder(id1, id2);
    }
}
