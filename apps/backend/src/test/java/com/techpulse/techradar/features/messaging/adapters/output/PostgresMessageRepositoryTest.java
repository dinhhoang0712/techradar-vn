package com.techpulse.techradar.features.messaging.adapters.output;

import com.techpulse.techradar.features.messaging.ports.MessageRepository.AttachmentInput;
import com.techpulse.techradar.features.messaging.ports.MessageRepository.AttachmentRow;
import com.techpulse.techradar.features.messaging.ports.MessageRepository.MessageRow;
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

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the SQL text, bind order/values, and row-mapping for {@link PostgresMessageRepository}.
 */
@ExtendWith(MockitoExtension.class)
class PostgresMessageRepositoryTest {

    @Mock
    private DatabaseClient dbClient;
    @Mock
    private DatabaseClient.GenericExecuteSpec executeSpec;
    @Mock
    private RowsFetchSpec<MessageRow> rowsFetchSpec;
    @Mock
    private RowsFetchSpec<AttachmentRow> attachmentRowsFetchSpec;
    @Mock
    private FetchSpec<Map<String, Object>> fetchSpec;
    @Mock
    private Row row;
    @Mock
    private RowMetadata rowMetadata;

    private PostgresMessageRepository repository;

    @BeforeEach
    void setUp() {
        repository = new PostgresMessageRepository(dbClient);
    }

    @SuppressWarnings("unchecked")
    private BiFunction<Row, RowMetadata, MessageRow> captureRowMapper() {
        ArgumentCaptor<BiFunction<Row, RowMetadata, MessageRow>> captor = ArgumentCaptor.forClass(BiFunction.class);
        verify(executeSpec).map(captor.capture());
        return captor.getValue();
    }

    private void stubRow(UUID id, UUID conversationId, UUID senderId, String content,
                          LocalDateTime createdAt, LocalDateTime readAt) {
        stubRow(id, conversationId, senderId, content, createdAt, readAt, null, null, null);
    }

    private void stubRow(UUID id, UUID conversationId, UUID senderId, String content,
                          LocalDateTime createdAt, LocalDateTime readAt,
                          String attachmentContentType, String attachmentFilename, Integer attachmentSize) {
        when(row.get("id", UUID.class)).thenReturn(id);
        when(row.get("conversation_id", UUID.class)).thenReturn(conversationId);
        when(row.get("sender_id", UUID.class)).thenReturn(senderId);
        when(row.get("content", String.class)).thenReturn(content);
        when(row.get("created_at", LocalDateTime.class)).thenReturn(createdAt);
        when(row.get("read_at", LocalDateTime.class)).thenReturn(readAt);
        when(row.get("attachment_content_type", String.class)).thenReturn(attachmentContentType);
        when(row.get("attachment_filename", String.class)).thenReturn(attachmentFilename);
        when(row.get("attachment_size", Integer.class)).thenReturn(attachmentSize);
    }

    private static final String INSERT_SQL =
            "INSERT INTO direct_message (id, conversation_id, sender_id, content, created_at, " +
            "attachment_content_type, attachment_filename, attachment_size, attachment_data) " +
            "VALUES (:id, :conversation_id, :sender_id, :content, :created_at, " +
            ":attachment_content_type, :attachment_filename, :attachment_size, :attachment_data) " +
            "RETURNING id, conversation_id, sender_id, content, created_at, read_at, " +
            "attachment_content_type, attachment_filename, attachment_size";

    @Test
    @SuppressWarnings("unchecked")
    void insert_withNoAttachment_bindsCoreColumnsAndNullsOutAttachmentColumns() {
        UUID messageId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        UUID senderId = UUID.randomUUID();
        LocalDateTime createdAt = LocalDateTime.now();

        when(dbClient.sql(INSERT_SQL)).thenReturn(executeSpec);
        when(executeSpec.bind(anyString(), any())).thenReturn(executeSpec);
        when(executeSpec.bindNull(anyString(), any())).thenReturn(executeSpec);
        when(executeSpec.map(any(BiFunction.class))).thenReturn(rowsFetchSpec);

        MessageRow expected = new MessageRow(messageId, conversationId, senderId, "hi", createdAt, null, null, null, null);
        when(rowsFetchSpec.one()).thenReturn(Mono.just(expected));

        StepVerifier.create(repository.insert(messageId, conversationId, senderId, "hi", createdAt, null))
                .expectNext(expected)
                .verifyComplete();

        verify(executeSpec).bind("id", messageId);
        verify(executeSpec).bind("conversation_id", conversationId);
        verify(executeSpec).bind("sender_id", senderId);
        verify(executeSpec).bind("content", "hi");
        verify(executeSpec).bind("created_at", createdAt);
        verify(executeSpec).bindNull("attachment_content_type", String.class);
        verify(executeSpec).bindNull("attachment_filename", String.class);
        verify(executeSpec).bindNull("attachment_size", Integer.class);
        verify(executeSpec).bindNull("attachment_data", byte[].class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void insert_withAnAttachment_bindsAllFourAttachmentColumns() {
        UUID messageId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        UUID senderId = UUID.randomUUID();
        LocalDateTime createdAt = LocalDateTime.now();
        AttachmentInput attachment = new AttachmentInput("image/png", "photo.png", 3, new byte[]{1, 2, 3});

        when(dbClient.sql(INSERT_SQL)).thenReturn(executeSpec);
        when(executeSpec.bind(anyString(), any())).thenReturn(executeSpec);
        when(executeSpec.map(any(BiFunction.class))).thenReturn(rowsFetchSpec);
        when(rowsFetchSpec.one()).thenReturn(Mono.empty());

        repository.insert(messageId, conversationId, senderId, "hi", createdAt, attachment).subscribe();

        verify(executeSpec).bind("attachment_content_type", "image/png");
        verify(executeSpec).bind("attachment_filename", "photo.png");
        verify(executeSpec).bind("attachment_size", 3);
        verify(executeSpec).bind("attachment_data", attachment.data());
    }

    @Test
    @SuppressWarnings("unchecked")
    void insert_rowMapper_mapsEveryColumn_includingAttachmentMetadata() {
        when(dbClient.sql(anyString())).thenReturn(executeSpec);
        when(executeSpec.bind(anyString(), any())).thenReturn(executeSpec);
        when(executeSpec.bindNull(anyString(), any())).thenReturn(executeSpec);
        when(executeSpec.map(any(BiFunction.class))).thenReturn(rowsFetchSpec);
        when(rowsFetchSpec.one()).thenReturn(Mono.empty());

        UUID id = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        UUID senderId = UUID.randomUUID();
        LocalDateTime createdAt = LocalDateTime.now();
        stubRow(id, conversationId, senderId, "hello there", createdAt, null, "image/png", "photo.png", 42);

        repository.insert(id, conversationId, senderId, "hello there", createdAt, null).subscribe();

        MessageRow mapped = captureRowMapper().apply(row, rowMetadata);
        assertThat(mapped.id()).isEqualTo(id);
        assertThat(mapped.conversationId()).isEqualTo(conversationId);
        assertThat(mapped.senderId()).isEqualTo(senderId);
        assertThat(mapped.content()).isEqualTo("hello there");
        assertThat(mapped.createdAt()).isEqualTo(createdAt);
        assertThat(mapped.readAt()).isNull();
        assertThat(mapped.attachmentContentType()).isEqualTo("image/png");
        assertThat(mapped.attachmentFilename()).isEqualTo("photo.png");
        assertThat(mapped.attachmentSize()).isEqualTo(42);
    }

    @Test
    @SuppressWarnings("unchecked")
    void findByConversation_bindsConversationIdLimitOffset_orderedOldestFirst() {
        UUID conversationId = UUID.randomUUID();

        when(dbClient.sql(
                "SELECT id, conversation_id, sender_id, content, created_at, read_at, " +
                        "attachment_content_type, attachment_filename, attachment_size FROM direct_message " +
                        "WHERE conversation_id = :conversation_id " +
                        "ORDER BY created_at ASC LIMIT :limit OFFSET :offset"))
                .thenReturn(executeSpec);
        when(executeSpec.bind(anyString(), any())).thenReturn(executeSpec);
        when(executeSpec.map(any(BiFunction.class))).thenReturn(rowsFetchSpec);
        when(rowsFetchSpec.all()).thenReturn(Flux.empty());

        StepVerifier.create(repository.findByConversation(conversationId, 50, 10)).verifyComplete();

        verify(executeSpec).bind("conversation_id", conversationId);
        verify(executeSpec).bind("limit", 50);
        verify(executeSpec).bind("offset", 10);
    }

    @Test
    @SuppressWarnings("unchecked")
    void findByConversation_rowMapper_mapsARowWithAReadAtTimestamp() {
        when(dbClient.sql(anyString())).thenReturn(executeSpec);
        when(executeSpec.bind(anyString(), any())).thenReturn(executeSpec);
        when(executeSpec.map(any(BiFunction.class))).thenReturn(rowsFetchSpec);
        when(rowsFetchSpec.all()).thenReturn(Flux.empty());

        UUID id = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        UUID senderId = UUID.randomUUID();
        LocalDateTime createdAt = LocalDateTime.now().minusMinutes(5);
        LocalDateTime readAt = LocalDateTime.now();
        stubRow(id, conversationId, senderId, "read message", createdAt, readAt);

        repository.findByConversation(conversationId, 50, 0).subscribe();

        MessageRow mapped = captureRowMapper().apply(row, rowMetadata);
        assertThat(mapped.readAt()).isEqualTo(readAt);
        assertThat(mapped.content()).isEqualTo("read message");
        assertThat(mapped.attachmentContentType()).isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void findById_bindsId_andReturnsTheRow() {
        UUID id = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        UUID senderId = UUID.randomUUID();
        LocalDateTime createdAt = LocalDateTime.now();

        when(dbClient.sql(
                "SELECT id, conversation_id, sender_id, content, created_at, read_at, " +
                        "attachment_content_type, attachment_filename, attachment_size FROM direct_message " +
                        "WHERE id = :id"))
                .thenReturn(executeSpec);
        when(executeSpec.bind(anyString(), any())).thenReturn(executeSpec);
        when(executeSpec.map(any(BiFunction.class))).thenReturn(rowsFetchSpec);
        MessageRow expected = new MessageRow(id, conversationId, senderId, "hi", createdAt, null, null, null, null);
        when(rowsFetchSpec.one()).thenReturn(Mono.just(expected));

        StepVerifier.create(repository.findById(id)).expectNext(expected).verifyComplete();

        verify(executeSpec).bind("id", id);
    }

    @Test
    @SuppressWarnings("unchecked")
    void findAttachmentData_bindsId_andOnlyReturnsRowsWithNonNullAttachmentData() {
        UUID id = UUID.randomUUID();
        byte[] bytes = {1, 2, 3};

        when(dbClient.sql(
                "SELECT attachment_content_type, attachment_filename, attachment_data FROM direct_message " +
                        "WHERE id = :id AND attachment_data IS NOT NULL"))
                .thenReturn(executeSpec);
        when(executeSpec.bind(anyString(), any())).thenReturn(executeSpec);
        when(executeSpec.map(any(BiFunction.class))).thenReturn(attachmentRowsFetchSpec);
        when(row.get("attachment_content_type", String.class)).thenReturn("image/png");
        when(row.get("attachment_filename", String.class)).thenReturn("photo.png");
        when(row.get("attachment_data", byte[].class)).thenReturn(bytes);
        AttachmentRow expected = new AttachmentRow("image/png", "photo.png", bytes);
        when(attachmentRowsFetchSpec.one()).thenReturn(Mono.just(expected));

        StepVerifier.create(repository.findAttachmentData(id)).expectNext(expected).verifyComplete();

        verify(executeSpec).bind("id", id);
    }

    @Test
    void markRead_bindsConversationIdAndReaderId_andCompletes() {
        UUID conversationId = UUID.randomUUID();
        UUID readerId = UUID.randomUUID();

        when(dbClient.sql(
                "UPDATE direct_message SET read_at = now() " +
                        "WHERE conversation_id = :conversation_id AND sender_id <> :reader_id AND read_at IS NULL"))
                .thenReturn(executeSpec);
        when(executeSpec.bind("conversation_id", conversationId)).thenReturn(executeSpec);
        when(executeSpec.bind("reader_id", readerId)).thenReturn(executeSpec);
        when(executeSpec.fetch()).thenReturn(fetchSpec);
        when(fetchSpec.rowsUpdated()).thenReturn(Mono.just(3L));

        StepVerifier.create(repository.markRead(conversationId, readerId)).verifyComplete();

        verify(executeSpec).bind("conversation_id", conversationId);
        verify(executeSpec).bind("reader_id", readerId);
    }

    @Test
    void markRead_completes_evenWhenNoRowsWereUpdated() {
        when(dbClient.sql(anyString())).thenReturn(executeSpec);
        when(executeSpec.bind(anyString(), any())).thenReturn(executeSpec);
        when(executeSpec.fetch()).thenReturn(fetchSpec);
        when(fetchSpec.rowsUpdated()).thenReturn(Mono.just(0L));

        StepVerifier.create(repository.markRead(UUID.randomUUID(), UUID.randomUUID())).verifyComplete();
    }
}
