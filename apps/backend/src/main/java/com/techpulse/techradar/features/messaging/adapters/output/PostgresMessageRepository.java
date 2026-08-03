package com.techpulse.techradar.features.messaging.adapters.output;

import com.techpulse.techradar.features.messaging.ports.MessageRepository;
import io.r2dbc.spi.Row;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class PostgresMessageRepository implements MessageRepository {

    private final DatabaseClient dbClient;

    @Override
    public Mono<MessageRow> insert(UUID messageId, UUID conversationId, UUID senderId, String content, LocalDateTime createdAt,
                                    AttachmentInput attachment) {
        DatabaseClient.GenericExecuteSpec spec = dbClient.sql(
                "INSERT INTO direct_message (id, conversation_id, sender_id, content, created_at, " +
                "attachment_content_type, attachment_filename, attachment_size, attachment_data) " +
                "VALUES (:id, :conversation_id, :sender_id, :content, :created_at, " +
                ":attachment_content_type, :attachment_filename, :attachment_size, :attachment_data) " +
                "RETURNING id, conversation_id, sender_id, content, created_at, read_at, " +
                "attachment_content_type, attachment_filename, attachment_size")
                .bind("id", messageId)
                .bind("conversation_id", conversationId)
                .bind("sender_id", senderId)
                .bind("content", content)
                .bind("created_at", createdAt);
        spec = attachment != null
                ? spec.bind("attachment_content_type", attachment.contentType())
                        .bind("attachment_filename", attachment.filename())
                        .bind("attachment_size", attachment.size())
                        .bind("attachment_data", attachment.data())
                : spec.bindNull("attachment_content_type", String.class)
                        .bindNull("attachment_filename", String.class)
                        .bindNull("attachment_size", Integer.class)
                        .bindNull("attachment_data", byte[].class);
        return spec.map((row, meta) -> mapRow(row)).one();
    }

    @Override
    public Flux<MessageRow> findByConversation(UUID conversationId, int limit, int offset) {
        return dbClient.sql(
                "SELECT id, conversation_id, sender_id, content, created_at, read_at, " +
                "attachment_content_type, attachment_filename, attachment_size FROM direct_message " +
                "WHERE conversation_id = :conversation_id " +
                "ORDER BY created_at ASC LIMIT :limit OFFSET :offset")
                .bind("conversation_id", conversationId)
                .bind("limit", limit)
                .bind("offset", offset)
                .map((row, meta) -> mapRow(row))
                .all();
    }

    @Override
    public Mono<MessageRow> findById(UUID messageId) {
        return dbClient.sql(
                "SELECT id, conversation_id, sender_id, content, created_at, read_at, " +
                "attachment_content_type, attachment_filename, attachment_size FROM direct_message " +
                "WHERE id = :id")
                .bind("id", messageId)
                .map((row, meta) -> mapRow(row))
                .one();
    }

    @Override
    public Mono<AttachmentRow> findAttachmentData(UUID messageId) {
        return dbClient.sql(
                "SELECT attachment_content_type, attachment_filename, attachment_data FROM direct_message " +
                "WHERE id = :id AND attachment_data IS NOT NULL")
                .bind("id", messageId)
                .map((row, meta) -> new AttachmentRow(
                        row.get("attachment_content_type", String.class),
                        row.get("attachment_filename", String.class),
                        row.get("attachment_data", byte[].class)))
                .one();
    }

    @Override
    public Mono<Void> markRead(UUID conversationId, UUID readerId) {
        return dbClient.sql(
                "UPDATE direct_message SET read_at = now() " +
                "WHERE conversation_id = :conversation_id AND sender_id <> :reader_id AND read_at IS NULL")
                .bind("conversation_id", conversationId)
                .bind("reader_id", readerId)
                .fetch().rowsUpdated().then();
    }

    private static MessageRow mapRow(Row row) {
        return new MessageRow(
                row.get("id", UUID.class),
                row.get("conversation_id", UUID.class),
                row.get("sender_id", UUID.class),
                row.get("content", String.class),
                row.get("created_at", LocalDateTime.class),
                row.get("read_at", LocalDateTime.class),
                row.get("attachment_content_type", String.class),
                row.get("attachment_filename", String.class),
                row.get("attachment_size", Integer.class)
        );
    }
}
