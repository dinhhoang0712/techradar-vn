package com.techpulse.techradar.features.system.adapters.output;

import com.techpulse.techradar.features.system.domain.AppSettings;
import com.techpulse.techradar.features.system.ports.SettingsRepository;
import io.r2dbc.spi.Row;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

/**
 * PostgreSQL adapter for settings repository.
 */
@Component
@RequiredArgsConstructor
public class PostgresSettingsRepository implements SettingsRepository {

    private final DatabaseClient dbClient;

    @Override
    public Mono<AppSettings> getSetting(String key) {
        return dbClient.sql(
                "SELECT key, value, description, updated_at FROM settings WHERE key = :key"
        )
                .bind("key", key)
                .map(row -> mapRowToSettings(row))
                .one();
    }

    @Override
    public Flux<AppSettings> getAllSettings() {
        return dbClient.sql(
                "SELECT key, value, description, updated_at FROM settings ORDER BY key"
        )
                .map(row -> mapRowToSettings(row))
                .all();
    }

    @Override
    public Mono<AppSettings> saveSetting(AppSettings settings) {
        LocalDateTime now = LocalDateTime.now();
        settings.setUpdatedAt(now);

        return dbClient.sql(
                "INSERT INTO settings (key, value, description, updated_at) " +
                "VALUES (:key, :value, :description, :updated_at) " +
                "ON CONFLICT (key) DO UPDATE SET " +
                "value = EXCLUDED.value, description = EXCLUDED.description, updated_at = EXCLUDED.updated_at"
        )
                .bind("key", settings.getKey())
                .bind("value", settings.getValue())
                .bind("description", settings.getDescription())
                .bind("updated_at", now)
                .fetch()
                .rowsUpdated()
                .map(rows -> settings);
    }

    @Override
    public Mono<Void> deleteSetting(String key) {
        return dbClient.sql("DELETE FROM settings WHERE key = :key")
                .bind("key", key)
                .fetch()
                .rowsUpdated()
                .then();
    }

    private AppSettings mapRowToSettings(Row row) {
        return AppSettings.builder()
                .key(row.get("key", String.class))
                .value(row.get("value", String.class))
                .description(row.get("description", String.class))
                .updatedAt(row.get("updated_at", LocalDateTime.class))
                .build();
    }
}
