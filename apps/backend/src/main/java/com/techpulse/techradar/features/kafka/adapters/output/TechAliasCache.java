package com.techpulse.techradar.features.kafka.adapters.output;

import com.techpulse.techradar.features.kafka.ports.TechAliasResolver;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Cache trong RAM cho {@code dp_tech_alias_map} (Postgres) — nguồn chuẩn hoá
 * tên Technology dùng CHUNG với silver/processor.py (Python data-platform),
 * để "Go"/"Golang", "ML"/"Machine Learning"... không tách thành 2 node khác
 * nhau trong Neo4j.
 * <p>
 * Refresh định kỳ (không phải mỗi message) — tra cache là 1 lần đọc
 * {@link Map} trong RAM, không có round-trip Postgres nào trên luồng Kafka
 * realtime.
 */
@Slf4j
@Component
public class TechAliasCache implements TechAliasResolver {

    private final DatabaseClient dbClient;
    private volatile Map<String, String> aliasByNormalized = Map.of();

    public TechAliasCache(DatabaseClient dbClient) {
        this.dbClient = dbClient;
    }

    @PostConstruct
    public void init() {
        refresh();
    }

    @Scheduled(fixedDelayString = "${app.tech-alias.refresh-ms:300000}")
    public void refresh() {
        dbClient.sql("SELECT alias_normalized, canonical_name FROM dp_tech_alias_map")
                .fetch()
                .all()
                .collectMap(
                        row -> (String) row.get("alias_normalized"),
                        row -> (String) row.get("canonical_name"))
                .subscribe(
                        map -> {
                            aliasByNormalized = Map.copyOf(map);
                            log.info("TechAliasCache refreshed: {} alias entries", map.size());
                        },
                        err -> log.warn("TechAliasCache refresh failed, giữ cache cũ ({} entries): {}",
                                aliasByNormalized.size(), err.getMessage()));
    }

    /**
     * Trả về tên canonical nếu có alias khớp (so khớp không phân biệt hoa/thường,
     * đã strip khoảng trắng) — ngược lại trả nguyên tên gốc (đã strip).
     */
    @Override
    public String resolve(String rawName) {
        if (rawName == null) {
            return null;
        }
        String trimmed = rawName.strip();
        String key = trimmed.toLowerCase();
        return aliasByNormalized.getOrDefault(key, trimmed);
    }
}
