package com.techpulse.techradar.features.radar.etl;

import com.techpulse.techradar.features.kafka.KafkaTopicConstants;
import com.techpulse.techradar.features.kafka.producer.KafkaProducerService;
import com.techpulse.techradar.features.notification.event.TrendAlertEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Session;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Rebuilds the Postgres {@code tech_analytics} time series from the Neo4j knowledge graph.
 * <p>
 * Signals: article mentions per month (reliable, articles carry {@code published_date}),
 * job postings per month when a job date exists, plus the current total job demand snapshot
 * (so /radar/top4 and /radar/top10 reflect real demand). The per-month chart "activity" prefers
 * job postings and falls back to article mentions.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RadarAnalyticsEtlService {

    private final Driver driver;
    private final DatabaseClient dbClient;
    private final KafkaProducerService kafkaProducer;

    /** Minimum month-over-month growth (%) for a technology to raise a trend alert. */
    @Value("${app.notifications.trend-threshold:30}")
    private double trendThreshold;

    /** Internal projection of one (technology, month) analytics row. */
    private record Row(String tech, LocalDate month, int jobCount, int articleCount,
                       double growthRate, double yoyGrowth, double momGrowth, Integer ranking) {
    }

    public Mono<Long> rebuild() {
        return Mono.fromCallable(this::computeRows)
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(rows -> Flux.fromIterable(rows)
                        .concatMap(this::upsert)
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
    private void emitTrendAlerts(List<Row> rows) {
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

    // ---- Read + aggregate from Neo4j -----------------------------------------

    private List<Row> computeRows() {
        // tech -> (YearMonth -> [jobCount, articleCount])
        Map<String, Map<YearMonth, int[]>> data = new HashMap<>();
        Map<String, Integer> snapshot = new HashMap<>();

        try (Session session = driver.session()) {
            // Article mentions per tech per month.
            String articleQ = "MATCH (t:Technology)<-[:MENTIONS]-(a:Article) " +
                    "WHERE a.published_date IS NOT NULL " +
                    "WITH t.name AS tech, substring(toString(a.published_date), 0, 7) AS ym " +
                    "WHERE ym IS NOT NULL " +
                    "RETURN tech, ym, count(*) AS c";
            for (Record rec : session.run(articleQ).list()) {
                bucket(data, rec, 1);
            }

            // Job postings per tech per month (only where a job date exists).
            String jobMonthQ = "MATCH (t:Technology)<-[:REQUIRES]-(j:Job) " +
                    "WITH t.name AS tech, j, " +
                    "     substring(toString(coalesce(j.posted_date, j.due_date, j.created_at)), 0, 7) AS ym " +
                    "WHERE ym IS NOT NULL " +
                    "RETURN tech, ym, count(DISTINCT j) AS c";
            for (Record rec : session.run(jobMonthQ).list()) {
                bucket(data, rec, 0);
            }

            // Current total job demand per tech (snapshot).
            String snapQ = "MATCH (t:Technology)<-[:REQUIRES]-(j:Job) " +
                    "RETURN t.name AS tech, count(DISTINCT j) AS c";
            for (Record rec : session.run(snapQ).list()) {
                if (!rec.get("tech").isNull()) {
                    snapshot.put(rec.get("tech").asString(), rec.get("c").asInt());
                }
            }
        }

        // Fold the current-demand snapshot into the current month.
        YearMonth current = YearMonth.now();
        snapshot.forEach((tech, count) -> {
            int[] cell = data.computeIfAbsent(tech, k -> new HashMap<>())
                    .computeIfAbsent(current, k -> new int[2]);
            cell[0] = Math.max(cell[0], count);
        });

        // Rank technologies by current demand (descending).
        Map<String, Integer> rankByTech = new HashMap<>();
        List<Map.Entry<String, Integer>> ranked = new ArrayList<>(snapshot.entrySet());
        ranked.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        for (int i = 0; i < ranked.size(); i++) {
            rankByTech.put(ranked.get(i).getKey(), i + 1);
        }

        return buildRows(data, rankByTech, current);
    }

    private void bucket(Map<String, Map<YearMonth, int[]>> data, Record rec, int index) {
        if (rec.get("tech").isNull()) {
            return;
        }
        YearMonth ym = parseYearMonth(rec.get("ym").asString());
        if (ym == null) {
            return;
        }
        int[] cell = data.computeIfAbsent(rec.get("tech").asString(), k -> new HashMap<>())
                .computeIfAbsent(ym, k -> new int[2]);
        cell[index] += rec.get("c").asInt();
    }

    private List<Row> buildRows(Map<String, Map<YearMonth, int[]>> data,
                                Map<String, Integer> rankByTech, YearMonth current) {
        List<Row> rows = new ArrayList<>();
        for (Map.Entry<String, Map<YearMonth, int[]>> techEntry : data.entrySet()) {
            String tech = techEntry.getKey();
            Map<YearMonth, int[]> months = techEntry.getValue();

            // activity per month = jobCount or, if none, articleCount.
            Map<YearMonth, Integer> activity = new LinkedHashMap<>();
            months.forEach((ym, cell) -> activity.put(ym, cell[0] > 0 ? cell[0] : cell[1]));

            for (Map.Entry<YearMonth, int[]> me : months.entrySet()) {
                YearMonth ym = me.getKey();
                int job = me.getValue()[0];
                int article = me.getValue()[1];
                int act = activity.get(ym);

                double mom = growth(act, activity.get(ym.minusMonths(1)));
                double yoy = growth(act, activity.get(ym.minusMonths(12)));
                Integer rank = ym.equals(current) ? rankByTech.get(tech) : null;

                rows.add(new Row(tech, ym.atDay(1), job, article, mom, yoy, mom, rank));
            }
        }
        return rows;
    }

    /** Percentage growth of {@code current} vs {@code previous}; 0 when previous is missing/zero. */
    private double growth(int current, Integer previous) {
        if (previous == null || previous == 0) {
            return 0.0;
        }
        return Math.round(((current - previous) / (double) previous) * 10000.0) / 100.0;
    }

    // ---- Write to Postgres ----------------------------------------------------

    private Mono<Long> upsert(Row row) {
        DatabaseClient.GenericExecuteSpec spec = dbClient.sql(
                "INSERT INTO tech_analytics " +
                "(technology_name, month, job_count, article_count, growth_rate, yoy_growth, mom_growth, ranking) " +
                "VALUES (:tech, :month, :job, :article, :growth, :yoy, :mom, :ranking) " +
                "ON CONFLICT (technology_name, month) DO UPDATE SET " +
                "job_count = EXCLUDED.job_count, article_count = EXCLUDED.article_count, " +
                "growth_rate = EXCLUDED.growth_rate, yoy_growth = EXCLUDED.yoy_growth, " +
                "mom_growth = EXCLUDED.mom_growth, ranking = EXCLUDED.ranking"
        )
                .bind("tech", row.tech())
                .bind("month", row.month())
                .bind("job", row.jobCount())
                .bind("article", row.articleCount())
                .bind("growth", row.growthRate())
                .bind("yoy", row.yoyGrowth())
                .bind("mom", row.momGrowth());
        spec = row.ranking() != null ? spec.bind("ranking", row.ranking()) : spec.bindNull("ranking", Integer.class);

        return spec.fetch().rowsUpdated();
    }

    private static YearMonth parseYearMonth(String ym) {
        try {
            return YearMonth.parse(ym); // expects yyyy-MM
        } catch (RuntimeException e) {
            return null;
        }
    }
}
