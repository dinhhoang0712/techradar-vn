package com.techpulse.techradar.features.system.adapters.output;

import com.techpulse.techradar.features.system.domain.CmsContent;
import com.techpulse.techradar.features.system.ports.CmsRepository;
import io.r2dbc.spi.Row;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * PostgreSQL adapter for {@code cms_content}.
 */
@Repository
@RequiredArgsConstructor
public class PostgresCmsRepository implements CmsRepository {

    private static final String COLUMNS = "id, title, type, content_date, status, created_at, updated_at";

    private final DatabaseClient dbClient;

    @Override
    public Flux<CmsContent> findAll() {
        return dbClient.sql("SELECT " + COLUMNS + " FROM cms_content ORDER BY created_at DESC")
                .map((row, meta) -> mapRow(row))
                .all();
    }

    @Override
    public Mono<CmsContent> findById(String id) {
        return dbClient.sql("SELECT " + COLUMNS + " FROM cms_content WHERE id = :id")
                .bind("id", UUID.fromString(id))
                .map((row, meta) -> mapRow(row))
                .one();
    }

    @Override
    public Mono<CmsContent> insert(CmsContent c) {
        UUID id = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        c.setId(id);
        c.setCreatedAt(now);
        c.setUpdatedAt(now);

        DatabaseClient.GenericExecuteSpec spec = dbClient.sql(
                "INSERT INTO cms_content (id, title, type, content_date, status, created_at, updated_at) " +
                "VALUES (:id, :title, :type, :content_date, :status, :created_at, :updated_at)")
                .bind("id", id)
                .bind("title", c.getTitle())
                .bind("status", c.getStatus() != null ? c.getStatus() : "Pending")
                .bind("created_at", now)
                .bind("updated_at", now);
        spec = bindNullable(spec, "type", c.getType());
        spec = bindNullableDate(spec, "content_date", c.getContentDate());

        return spec.fetch().rowsUpdated().thenReturn(c);
    }

    @Override
    public Mono<CmsContent> update(CmsContent c) {
        LocalDateTime now = LocalDateTime.now();
        c.setUpdatedAt(now);

        DatabaseClient.GenericExecuteSpec spec = dbClient.sql(
                "UPDATE cms_content SET title = :title, type = :type, content_date = :content_date, " +
                "status = :status, updated_at = :updated_at WHERE id = :id")
                .bind("id", c.getId())
                .bind("title", c.getTitle())
                .bind("status", c.getStatus() != null ? c.getStatus() : "Pending")
                .bind("updated_at", now);
        spec = bindNullable(spec, "type", c.getType());
        spec = bindNullableDate(spec, "content_date", c.getContentDate());

        return spec.fetch().rowsUpdated().thenReturn(c);
    }

    @Override
    public Mono<Long> deleteById(String id) {
        return dbClient.sql("DELETE FROM cms_content WHERE id = :id")
                .bind("id", UUID.fromString(id))
                .fetch()
                .rowsUpdated();
    }

    private CmsContent mapRow(Row row) {
        return CmsContent.builder()
                .id(row.get("id", UUID.class))
                .title(row.get("title", String.class))
                .type(row.get("type", String.class))
                .contentDate(row.get("content_date", LocalDate.class))
                .status(row.get("status", String.class))
                .createdAt(row.get("created_at", LocalDateTime.class))
                .updatedAt(row.get("updated_at", LocalDateTime.class))
                .build();
    }

    private static DatabaseClient.GenericExecuteSpec bindNullable(
            DatabaseClient.GenericExecuteSpec spec, String name, String value) {
        return value != null ? spec.bind(name, value) : spec.bindNull(name, String.class);
    }

    private static DatabaseClient.GenericExecuteSpec bindNullableDate(
            DatabaseClient.GenericExecuteSpec spec, String name, LocalDate value) {
        return value != null ? spec.bind(name, value) : spec.bindNull(name, LocalDate.class);
    }
}
