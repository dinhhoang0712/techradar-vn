package com.techpulse.techradar.features.radar.etl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techpulse.techradar.features.kafka.KafkaTopicConstants;
import com.techpulse.techradar.features.notification.event.TrendAlertEvent;
import com.techpulse.techradar.features.radar.domain.TechAnalyticsRow;
import com.techpulse.techradar.features.radar.domain.TechAnalyticsTransformer;
import com.techpulse.techradar.features.radar.ports.RadarGraphReadPort;
import com.techpulse.techradar.features.radar.ports.TechAnalyticsWritePort;
import com.techpulse.techradar.features.system.application.CmsService;
import com.techpulse.techradar.shared.outbox.OutboxEventRepository;
import com.techpulse.techradar.shared.redis.RedisJsonStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;

/**
 * Rebuilds the Postgres {@code tech_analytics} time series from the Neo4j knowledge graph.
 * <p>
 * Signals: article mentions per month (reliable, articles carry {@code published_date}),
 * job postings per month when a job date exists, plus the current total job demand snapshot
 * (so /radar/top4 and /radar/top10 reflect real demand). The per-month chart "activity" prefers
 * job postings and falls back to article mentions.
 * <p>
 * Orchestration only: graph reads and Postgres writes live behind {@link RadarGraphReadPort} and
 * {@link TechAnalyticsWritePort}; the scoring/ranking math lives in
 * {@link TechAnalyticsTransformer}, which has no I/O and is unit-tested in isolation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RadarAnalyticsEtlService {

    private static final String STATUS_KEY = "radar:status";
    /** How many top-ranked technologies go into the CMS "keyword digest" row per rebuild. */
    private static final int KEYWORD_DIGEST_TOP_N = 5;

    private final RadarGraphReadPort graphReadPort;
    private final TechAnalyticsWritePort writePort;
    private final OutboxEventRepository outboxEventRepository;
    private final TransactionalOperator transactionalOperator;
    private final ReactiveStringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final CmsService cmsService;

    /** Minimum month-over-month growth (%) for a technology to raise a trend alert. */
    @Value("${app.notifications.trend-threshold:30}")
    private double trendThreshold;

    /**
     * Wraps the rebuild with a {@code radar:status} Redis write (running -> idle, mirroring
     * {@code crawler:status}) so the admin live-metrics dashboard can show whether a rebuild is in
     * progress. The status write is fire-and-forget on both ends: a Redis hiccup must never fail
     * or delay the actual ETL work.
     */
    public Mono<Long> rebuild() {
        String startedAt = Instant.now().toString();
        return writeStatus(RadarStatus.running(startedAt))
                .then(Mono.fromCallable(this::computeRows)
                        .subscribeOn(Schedulers.boundedElastic())
                        .flatMap(rows -> persistRowsAndQueueTrendAlerts(rows)
                                .doOnSuccess(n -> {
                                    log.info("tech_analytics ETL upserted {} rows", n);
                                    writeKeywordDigest(rows);
                                })))
                .doOnSuccess(count -> writeStatus(RadarStatus.idle(startedAt, Instant.now().toString(), count)).subscribe())
                .doOnError(e -> {
                    log.error("tech_analytics ETL failed", e);
                    writeStatus(RadarStatus.idle(startedAt, Instant.now().toString(), null)).subscribe();
                });
    }

    private Mono<Void> writeStatus(RadarStatus status) {
        return RedisJsonStatus.write(redisTemplate, objectMapper, STATUS_KEY, status);
    }

    /**
     * Upserts every row and queues an {@code outbox_event} row per qualifying trend alert inside
     * ONE R2DBC transaction — a crash or Kafka outage between "tech_analytics committed" and
     * "alert actually published" can no longer silently drop the alert. The relay poller
     * ({@code shared.outbox.OutboxRelayScheduler}) publishes queued rows to Kafka separately,
     * outside this transaction. See {@code docs/adr/0005-transactional-outbox-trend-alerts.md}.
     */
    private Mono<Long> persistRowsAndQueueTrendAlerts(List<TechAnalyticsRow> rows) {
        List<TrendAlertEvent> alerts = trendAlerts(rows);
        Mono<Long> upserts = Flux.fromIterable(rows).concatMap(writePort::upsert).reduce(0L, Long::sum);
        Mono<Void> outboxInserts = Flux.fromIterable(alerts)
                .concatMap(alert -> outboxEventRepository.save(KafkaTopicConstants.TREND_ALERTS, alert))
                .then();
        return upserts.flatMap(count -> outboxInserts.thenReturn(count))
                .as(transactionalOperator::transactional)
                .doOnSuccess(count -> {
                    if (!alerts.isEmpty()) {
                        log.info("Queued {} trend alert(s) to outbox (threshold {}% MoM)", alerts.size(), trendThreshold);
                    }
                });
    }

    /** Technologies whose current-month demand grew at least {@link #trendThreshold}%. Pure — no I/O. */
    private List<TrendAlertEvent> trendAlerts(List<TechAnalyticsRow> rows) {
        LocalDate currentMonth = YearMonth.now().atDay(1);
        String monthLabel = YearMonth.now().toString();
        return rows.stream()
                .filter(r -> currentMonth.equals(r.month()))
                .filter(r -> r.jobCount() > 0)
                .filter(r -> r.momGrowth() >= trendThreshold)
                .map(r -> new TrendAlertEvent(r.tech(), r.momGrowth(), r.growthRate(), r.jobCount(), monthLabel))
                .toList();
    }

    /**
     * Writes a "Từ khóa nổi bật" (top technologies this month) row to {@code cms_content} — before
     * this, the admin CMS page's "Keyword" entries were static seed data with nothing ever
     * refreshing them. {@code status="Analyzed"}: the ETL only proposes the digest, an admin still
     * decides whether to publish it.
     */
    private void writeKeywordDigest(List<TechAnalyticsRow> rows) {
        LocalDate currentMonth = YearMonth.now().atDay(1);
        List<String> topNames = rows.stream()
                .filter(r -> currentMonth.equals(r.month()))
                .filter(r -> r.ranking() != null)
                .sorted(Comparator.comparingInt(TechAnalyticsRow::ranking))
                .map(TechAnalyticsRow::tech)
                .limit(KEYWORD_DIGEST_TOP_N)
                .toList();
        if (topNames.isEmpty()) {
            return;
        }
        String title = "Từ khóa nổi bật: " + String.join(", ", topNames);
        cmsService.create(title, "Keyword", currentMonth, "Analyzed")
                .onErrorResume(e -> Mono.empty())
                .subscribe();
    }

    /** Reads the raw graph signals and runs them through the pure {@link TechAnalyticsTransformer}. */
    private List<TechAnalyticsRow> computeRows() {
        return TechAnalyticsTransformer.compute(
                graphReadPort.findArticleMentionDates(),
                graphReadPort.findJobPostingDates(),
                graphReadPort.findJobDemandSnapshot(),
                YearMonth.now());
    }
}
