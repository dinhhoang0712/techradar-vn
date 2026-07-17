package com.techpulse.techradar.features.social.adapters.output;

import com.techpulse.techradar.features.social.ports.PostImageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class PostgresPostImageRepository implements PostImageRepository {

    private final DatabaseClient dbClient;

    @Override
    public Mono<Void> insert(UUID id, UUID postId, int ordinal, String contentType, byte[] data, LocalDateTime createdAt) {
        return dbClient.sql(
                "INSERT INTO post_image (id, post_id, ordinal, content_type, data, created_at) " +
                "VALUES (:id, :post_id, :ordinal, :content_type, :data, :created_at)")
                .bind("id", id)
                .bind("post_id", postId)
                .bind("ordinal", ordinal)
                .bind("content_type", contentType)
                .bind("data", data)
                .bind("created_at", createdAt)
                .fetch().rowsUpdated().then();
    }

    @Override
    public Mono<ImageRow> findById(UUID imageId) {
        return dbClient.sql("SELECT post_id, content_type, data FROM post_image WHERE id = :id")
                .bind("id", imageId)
                .map((row, meta) -> new ImageRow(
                        row.get("post_id", UUID.class),
                        row.get("content_type", String.class),
                        row.get("data", byte[].class)))
                .one();
    }
}
