package com.techpulse.techradar.features.messaging.adapters.output;

import com.techpulse.techradar.features.messaging.ports.ConversationRepository.ConversationRow;
import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.r2dbc.core.RowsFetchSpec;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.function.BiFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the SQL text, bind order/values, and row-mapping for {@link PostgresConversationRepository}
 * — in particular the {@code user_a_id < user_b_id} canonicalization in {@link
 * PostgresConversationRepository#findOrCreate}, which is exactly the kind of subtle, hand-rolled
 * ordering logic that silently breaks (violating the DB's {@code CHECK} constraint) without a
 * direct test.
 */
@ExtendWith(MockitoExtension.class)
class PostgresConversationRepositoryTest {

    @Mock
    private DatabaseClient dbClient;
    @Mock
    private DatabaseClient.GenericExecuteSpec executeSpec;
    @Mock
    private RowsFetchSpec<UUID> uuidRowsFetchSpec;
    @Mock
    private RowsFetchSpec<Boolean> booleanRowsFetchSpec;
    @Mock
    private RowsFetchSpec<Long> longRowsFetchSpec;
    @Mock
    private RowsFetchSpec<ConversationRow> conversationRowsFetchSpec;
    @Mock
    private Row row;
    @Mock
    private RowMetadata rowMetadata;

    private static final String FIND_OR_CREATE_SQL =
            "INSERT INTO conversation (id, user_a_id, user_b_id) VALUES (:id, :a, :b) " +
                    "ON CONFLICT (user_a_id, user_b_id) DO UPDATE SET user_a_id = conversation.user_a_id " +
                    "RETURNING id";

    private PostgresConversationRepository repository;

    @BeforeEach
    void setUp() {
        repository = new PostgresConversationRepository(dbClient);
    }

    @Test
    @SuppressWarnings("unchecked")
    void findOrCreate_canonicalizesPair_whenFirstArgumentIsTheLexicographicallySmallerUuid() {
        UUID small = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID large = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");

        when(dbClient.sql(FIND_OR_CREATE_SQL)).thenReturn(executeSpec);
        when(executeSpec.bind(eq("id"), any(UUID.class))).thenReturn(executeSpec);
        when(executeSpec.bind(eq("a"), any(UUID.class))).thenReturn(executeSpec);
        when(executeSpec.bind(eq("b"), any(UUID.class))).thenReturn(executeSpec);
        when(executeSpec.map(any(BiFunction.class))).thenReturn(uuidRowsFetchSpec);
        when(uuidRowsFetchSpec.one()).thenReturn(Mono.just(UUID.randomUUID()));

        StepVerifier.create(repository.findOrCreate(small, large))
                .expectNextCount(1)
                .verifyComplete();

        ArgumentCaptor<UUID> aCaptor = ArgumentCaptor.forClass(UUID.class);
        ArgumentCaptor<UUID> bCaptor = ArgumentCaptor.forClass(UUID.class);
        verify(executeSpec, times(1)).bind(eq("a"), aCaptor.capture());
        verify(executeSpec, times(1)).bind(eq("b"), bCaptor.capture());
        assertThat(aCaptor.getValue()).isEqualTo(small);
        assertThat(bCaptor.getValue()).isEqualTo(large);
    }

    @Test
    @SuppressWarnings("unchecked")
    void findOrCreate_canonicalizesPair_whenFirstArgumentIsTheLexicographicallyLargerUuid() {
        UUID small = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID large = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");

        when(dbClient.sql(FIND_OR_CREATE_SQL)).thenReturn(executeSpec);
        when(executeSpec.bind(eq("id"), any(UUID.class))).thenReturn(executeSpec);
        when(executeSpec.bind(eq("a"), any(UUID.class))).thenReturn(executeSpec);
        when(executeSpec.bind(eq("b"), any(UUID.class))).thenReturn(executeSpec);
        when(executeSpec.map(any(BiFunction.class))).thenReturn(uuidRowsFetchSpec);
        when(uuidRowsFetchSpec.one()).thenReturn(Mono.just(UUID.randomUUID()));

        // Arguments passed in reverse (large, small) order this time.
        StepVerifier.create(repository.findOrCreate(large, small))
                .expectNextCount(1)
                .verifyComplete();

        ArgumentCaptor<UUID> aCaptor = ArgumentCaptor.forClass(UUID.class);
        ArgumentCaptor<UUID> bCaptor = ArgumentCaptor.forClass(UUID.class);
        verify(executeSpec, times(1)).bind(eq("a"), aCaptor.capture());
        verify(executeSpec, times(1)).bind(eq("b"), bCaptor.capture());
        // Same canonical pair as the previous test regardless of argument order.
        assertThat(aCaptor.getValue()).isEqualTo(small);
        assertThat(bCaptor.getValue()).isEqualTo(large);
    }

    @Test
    @SuppressWarnings("unchecked")
    void findOrCreate_bindsARandomIdAndReturnsTheMappedId() {
        UUID userX = UUID.randomUUID();
        UUID userY = UUID.randomUUID();
        UUID returnedId = UUID.randomUUID();

        when(dbClient.sql(FIND_OR_CREATE_SQL)).thenReturn(executeSpec);
        when(executeSpec.bind(anyString(), any())).thenReturn(executeSpec);
        when(executeSpec.map(any(BiFunction.class))).thenReturn(uuidRowsFetchSpec);
        when(uuidRowsFetchSpec.one()).thenReturn(Mono.just(returnedId));
        when(row.get("id", UUID.class)).thenReturn(returnedId);

        StepVerifier.create(repository.findOrCreate(userX, userY))
                .expectNext(returnedId)
                .verifyComplete();

        ArgumentCaptor<BiFunction<Row, RowMetadata, UUID>> mapperCaptor = ArgumentCaptor.forClass(BiFunction.class);
        verify(executeSpec).map(mapperCaptor.capture());
        assertThat(mapperCaptor.getValue().apply(row, rowMetadata)).isEqualTo(returnedId);
    }

    @Test
    @SuppressWarnings("unchecked")
    void isParticipant_bindsIdsAndReturnsTrue_whenRowSaysTrue() {
        UUID conversationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(dbClient.sql(
                "SELECT EXISTS(SELECT 1 FROM conversation WHERE id = :id AND (user_a_id = :user_id OR user_b_id = :user_id)) AS is_participant"))
                .thenReturn(executeSpec);
        when(executeSpec.bind("id", conversationId)).thenReturn(executeSpec);
        when(executeSpec.bind("user_id", userId)).thenReturn(executeSpec);
        when(executeSpec.map(any(BiFunction.class))).thenReturn(booleanRowsFetchSpec);
        when(booleanRowsFetchSpec.one()).thenReturn(Mono.just(true));

        StepVerifier.create(repository.isParticipant(conversationId, userId))
                .expectNext(true)
                .verifyComplete();

        verify(executeSpec).bind("id", conversationId);
        verify(executeSpec).bind("user_id", userId);
    }

    @Test
    @SuppressWarnings("unchecked")
    void isParticipant_defaultsToFalse_whenNoRowFound() {
        UUID conversationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(dbClient.sql(anyString())).thenReturn(executeSpec);
        when(executeSpec.bind(anyString(), any())).thenReturn(executeSpec);
        when(executeSpec.map(any(BiFunction.class))).thenReturn(booleanRowsFetchSpec);
        when(booleanRowsFetchSpec.one()).thenReturn(Mono.empty());

        StepVerifier.create(repository.isParticipant(conversationId, userId))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    @SuppressWarnings("unchecked")
    void isParticipant_rowMapper_treatsNullOrFalseAsFalse() {
        when(dbClient.sql(anyString())).thenReturn(executeSpec);
        when(executeSpec.bind(anyString(), any())).thenReturn(executeSpec);
        when(executeSpec.map(any(BiFunction.class))).thenReturn(booleanRowsFetchSpec);
        when(booleanRowsFetchSpec.one()).thenReturn(Mono.empty());
        when(row.get("is_participant", Boolean.class)).thenReturn(null);

        repository.isParticipant(UUID.randomUUID(), UUID.randomUUID()).subscribe();

        ArgumentCaptor<BiFunction<Row, RowMetadata, Boolean>> mapperCaptor = ArgumentCaptor.forClass(BiFunction.class);
        verify(executeSpec).map(mapperCaptor.capture());
        assertThat(mapperCaptor.getValue().apply(row, rowMetadata)).isFalse();
    }

    @Test
    @SuppressWarnings("unchecked")
    void otherParticipant_bindsIdsAndMapsOtherId() {
        UUID conversationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID otherId = UUID.randomUUID();

        when(dbClient.sql(
                "SELECT CASE WHEN user_a_id = :user_id THEN user_b_id ELSE user_a_id END AS other_id " +
                        "FROM conversation WHERE id = :id"))
                .thenReturn(executeSpec);
        when(executeSpec.bind("id", conversationId)).thenReturn(executeSpec);
        when(executeSpec.bind("user_id", userId)).thenReturn(executeSpec);
        when(executeSpec.map(any(BiFunction.class))).thenReturn(uuidRowsFetchSpec);
        when(uuidRowsFetchSpec.one()).thenReturn(Mono.just(otherId));
        when(row.get("other_id", UUID.class)).thenReturn(otherId);

        StepVerifier.create(repository.otherParticipant(conversationId, userId))
                .expectNext(otherId)
                .verifyComplete();

        ArgumentCaptor<BiFunction<Row, RowMetadata, UUID>> mapperCaptor = ArgumentCaptor.forClass(BiFunction.class);
        verify(executeSpec).map(mapperCaptor.capture());
        assertThat(mapperCaptor.getValue().apply(row, rowMetadata)).isEqualTo(otherId);
    }

    @Test
    @SuppressWarnings("unchecked")
    void findAllForUser_bindsUserIdLimitOffset_andMapsEveryColumnOfConversationRow() {
        UUID userId = UUID.randomUUID();

        when(dbClient.sql(anyString())).thenReturn(executeSpec);
        when(executeSpec.bind(anyString(), any())).thenReturn(executeSpec);
        when(executeSpec.map(any(BiFunction.class))).thenReturn(conversationRowsFetchSpec);
        when(conversationRowsFetchSpec.all()).thenReturn(Flux.empty());

        repository.findAllForUser(userId, 20, 0).subscribe();

        verify(executeSpec).bind("user_id", userId);
        verify(executeSpec).bind("limit", 20);
        verify(executeSpec).bind("offset", 0);

        UUID id = UUID.randomUUID();
        UUID otherId = UUID.randomUUID();
        LocalDateTime lastAt = LocalDateTime.now();
        UUID lastSender = UUID.randomUUID();
        when(row.get("id", UUID.class)).thenReturn(id);
        when(row.get("other_id", UUID.class)).thenReturn(otherId);
        when(row.get("other_name", String.class)).thenReturn("Jane Doe");
        when(row.get("other_avatar", String.class)).thenReturn("http://avatar");
        when(row.get("last_content", String.class)).thenReturn("hello");
        when(row.get("last_at", LocalDateTime.class)).thenReturn(lastAt);
        when(row.get("last_sender", UUID.class)).thenReturn(lastSender);
        when(row.get("unread_count", Long.class)).thenReturn(3L);

        ArgumentCaptor<BiFunction<Row, RowMetadata, ConversationRow>> mapperCaptor =
                ArgumentCaptor.forClass(BiFunction.class);
        verify(executeSpec).map(mapperCaptor.capture());
        ConversationRow mapped = mapperCaptor.getValue().apply(row, rowMetadata);

        assertThat(mapped.id()).isEqualTo(id);
        assertThat(mapped.otherUserId()).isEqualTo(otherId);
        assertThat(mapped.otherUserName()).isEqualTo("Jane Doe");
        assertThat(mapped.otherUserAvatarUrl()).isEqualTo("http://avatar");
        assertThat(mapped.lastMessageContent()).isEqualTo("hello");
        assertThat(mapped.lastMessageAt()).isEqualTo(lastAt);
        assertThat(mapped.lastMessageSenderId()).isEqualTo(lastSender);
        assertThat(mapped.unreadCount()).isEqualTo(3L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void countConversations_returnsMappedCount() {
        when(dbClient.sql("SELECT count(*) AS c FROM conversation")).thenReturn(executeSpec);
        when(executeSpec.map(any(BiFunction.class))).thenReturn(longRowsFetchSpec);
        when(longRowsFetchSpec.one()).thenReturn(Mono.just(5L));

        StepVerifier.create(repository.countConversations()).expectNext(5L).verifyComplete();
    }

    @Test
    @SuppressWarnings("unchecked")
    void countConversations_defaultsToZero_whenNoRow() {
        when(dbClient.sql("SELECT count(*) AS c FROM conversation")).thenReturn(executeSpec);
        when(executeSpec.map(any(BiFunction.class))).thenReturn(longRowsFetchSpec);
        when(longRowsFetchSpec.one()).thenReturn(Mono.empty());

        StepVerifier.create(repository.countConversations()).expectNext(0L).verifyComplete();
    }

    @Test
    @SuppressWarnings("unchecked")
    void countMessages_returnsMappedCount() {
        when(dbClient.sql("SELECT count(*) AS c FROM direct_message")).thenReturn(executeSpec);
        when(executeSpec.map(any(BiFunction.class))).thenReturn(longRowsFetchSpec);
        when(longRowsFetchSpec.one()).thenReturn(Mono.just(42L));

        StepVerifier.create(repository.countMessages()).expectNext(42L).verifyComplete();
    }

    @Test
    @SuppressWarnings("unchecked")
    void countMessagesSince_bindsSinceTimestamp_andReturnsMappedCount() {
        LocalDateTime since = LocalDateTime.now().minusDays(7);

        when(dbClient.sql("SELECT count(*) AS c FROM direct_message WHERE created_at >= :since"))
                .thenReturn(executeSpec);
        when(executeSpec.bind("since", since)).thenReturn(executeSpec);
        when(executeSpec.map(any(BiFunction.class))).thenReturn(longRowsFetchSpec);
        when(longRowsFetchSpec.one()).thenReturn(Mono.just(9L));

        StepVerifier.create(repository.countMessagesSince(since)).expectNext(9L).verifyComplete();

        verify(executeSpec).bind("since", since);
    }
}
