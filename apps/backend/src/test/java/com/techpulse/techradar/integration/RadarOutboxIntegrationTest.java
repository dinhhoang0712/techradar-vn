package com.techpulse.techradar.integration;

import org.junit.jupiter.api.Test;

import java.time.YearMonth;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end check that {@code POST /admin/analytics/rebuild} queues a real {@code outbox_event}
 * row inside the same Postgres transaction as the {@code tech_analytics} upsert — the behavior
 * {@link com.techpulse.techradar.features.radar.etl.RadarAnalyticsEtlServiceTest} covers with
 * mocks, exercised here against a real R2DBC transaction. See
 * {@code docs/adr/0005-transactional-outbox-trend-alerts.md}.
 */
class RadarOutboxIntegrationTest extends IntegrationTestSupport {

    private static final String ALERT_TECH = "OutboxAlertTech";

    @Test
    void rebuild_queuesTrendAlertToOutbox_whenGrowthCrossesThreshold() {
        String admin = adminToken();
        db.sql("DELETE FROM tech_analytics").fetch().rowsUpdated().block();
        db.sql("DELETE FROM outbox_event").fetch().rowsUpdated().block();
        seedGrowthSpike();

        web.post().uri("/api/v1/admin/analytics/rebuild").header("Authorization", bearer(admin))
                .exchange().expectStatus().isOk();

        Map<String, Object> row = db.sql(
                "SELECT topic, payload, status FROM outbox_event WHERE topic = :topic ORDER BY created_at DESC LIMIT 1")
                .bind("topic", "trend.alerts")
                .map((r, meta) -> Map.<String, Object>of(
                        "topic", r.get("topic", String.class),
                        "payload", r.get("payload", String.class),
                        "status", r.get("status", String.class)))
                .one()
                .block();

        assertThat(row).isNotNull();
        assertThat(row.get("payload")).asString().contains(ALERT_TECH);
        // Relay is disabled in tests (see IntegrationTestSupport) — the row must still be sitting
        // PENDING, proving the outbox write itself (not a downstream Kafka publish) is what the ETL
        // transaction guarantees.
        assertThat(row.get("status")).isEqualTo("PENDING");
    }

    /**
     * One job posted last month (baseline activity = 1) plus 20 more jobs requiring the same
     * technology today (current demand snapshot = 21) — comfortably above the default 30% MoM
     * threshold, same shape as {@code RadarAnalyticsEtlServiceTest.rebuild_publishesTrendAlert...}.
     */
    private void seedGrowthSpike() {
        String previousMonthDate = YearMonth.now().minusMonths(1).atDay(10).toString();
        try (var session = neo4j.session()) {
            session.run("MATCH (n) DETACH DELETE n");
            session.run(
                    "CREATE (t:Technology {name: $tech}) " +
                    "CREATE (j0:Job {title: 'Old Job', posted_date: $prevDate}) " +
                    "CREATE (j0)-[:REQUIRES]->(t)",
                    Map.of("tech", ALERT_TECH, "prevDate", previousMonthDate));
            for (int i = 0; i < 20; i++) {
                session.run(
                        "MATCH (t:Technology {name: $tech}) " +
                        "CREATE (j:Job {title: $title}) " +
                        "CREATE (j)-[:REQUIRES]->(t)",
                        Map.of("tech", ALERT_TECH, "title", "New Job " + i));
            }
        }
    }
}
