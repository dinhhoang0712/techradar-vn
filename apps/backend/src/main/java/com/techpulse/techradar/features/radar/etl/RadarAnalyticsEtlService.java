package com.techpulse.techradar.features.radar.etl;

import com.techpulse.techradar.features.kafka.KafkaTopicConstants;
import com.techpulse.techradar.features.kafka.producer.KafkaProducerService;
import com.techpulse.techradar.features.notification.event.TrendAlertEvent;
import com.techpulse.techradar.features.radar.domain.TechAnalyticsRow;
import com.techpulse.techradar.features.radar.domain.TechAnalyticsTransformer;
import com.techpulse.techradar.features.radar.ports.RadarGraphReadPort;
import com.techpulse.techradar.features.radar.ports.TechAnalyticsWritePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDate;
import java.time.YearMonth;
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

    private final RadarGraphReadPort graphReadPort;
    private final TechAnalyticsWritePort writePort;
    private final KafkaProducerService kafkaProducer;

    /** Minimum month-over-month growth (%) for a technology to raise a trend alert. */
    @Value("${app.notifications.trend-threshold:30}")
    private double trendThreshold;

    public Mono<Long> rebuild() {
        return Mono.fromCallable(this::computeRows)
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(rows -> Flux.fromIterable(rows)
                        .concatMap(writePort::upsert)
                        .reduce(0L, Long::sum)
                        .doOnSuccess(n -> {
                            log.info("tech_analytics ETL upserted {} rows", n);
                            emitTrendAlerts(rows);
                        }))
                .doOnError(e -> log.error("tech_analytics ETL failed", e));
    }

    /**
     * Publish a {@code trend.alerts} event for each technology whose current-month demand grew at
     * least {@link #trendThreshold}%. Producing is offloaded to a worker thread so an unreachable
     * Kafka broker never stalls the ETL completion.
     */
    private void emitTrendAlerts(List<TechAnalyticsRow> rows) {
        LocalDate currentMonth = YearMonth.now().atDay(1);
        String monthLabel = YearMonth.now().toString();
        List<TrendAlertEvent> alerts = rows.stream()
                .filter(r -> currentMonth.equals(r.month()))
                .filter(r -> r.jobCount() > 0)
                .filter(r -> r.momGrowth() >= trendThreshold)
                .map(r -> new TrendAlertEvent(r.tech(), r.momGrowth(), r.growthRate(), r.jobCount(), monthLabel))
                .toList();
        if (alerts.isEmpty()) {
            return;
        }
        Schedulers.boundedElastic().schedule(() -> {
            for (TrendAlertEvent alert : alerts) {
                try {
                    kafkaProducer.send(KafkaTopicConstants.TREND_ALERTS, alert);
                } catch (Exception e) {
                    log.warn("Could not publish trend alert for {} (Kafka unavailable?)", alert.getTechnology(), e);
                }
            }
            log.info("Published {} trend alert(s) (threshold {}% MoM)", alerts.size(), trendThreshold);
        });
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
