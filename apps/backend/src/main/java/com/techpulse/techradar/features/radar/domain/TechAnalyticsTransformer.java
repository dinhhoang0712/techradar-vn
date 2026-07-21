package com.techpulse.techradar.features.radar.domain;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure transform from raw graph signals to {@code tech_analytics} rows. No I/O of any kind —
 * everything here is unit-testable by constructing {@link TechDateSample}/{@link TechCount}
 * lists directly, without a live Neo4j or Postgres connection.
 * <p>
 * Combines three signals sourced from the knowledge graph:
 * <ul>
 *   <li>article-mention dates (reliable; articles carry {@code published_date})</li>
 *   <li>job-posting dates (best-effort; only when a job date exists)</li>
 *   <li>the current total job-demand snapshot (folded into the current month, and the basis for
 *       {@code ranking})</li>
 * </ul>
 * These aren't independent, swappable strategies — they all merge into one shared per-tech/month
 * bucket before the growth/ranking math runs — so this is one small pure class with a method per
 * step rather than a {@code Strategy} interface per signal.
 */
public final class TechAnalyticsTransformer {

    private TechAnalyticsTransformer() {
    }

    public static List<TechAnalyticsRow> compute(List<TechDateSample> articleMentions,
                                                   List<TechDateSample> jobPostingDates,
                                                   List<TechCount> jobDemandSnapshot,
                                                   YearMonth currentMonth) {
        // tech -> (YearMonth -> [jobCount, articleCount])
        Map<String, Map<YearMonth, int[]>> data = new HashMap<>();
        bucket(data, jobPostingDates, 0);
        bucket(data, articleMentions, 1);

        Map<String, Integer> snapshot = new HashMap<>();
        for (TechCount tc : jobDemandSnapshot) {
            if (tc.tech() != null) {
                snapshot.put(tc.tech(), tc.count());
            }
        }

        // Fold the current-demand snapshot into the current month.
        snapshot.forEach((tech, count) -> {
            int[] cell = data.computeIfAbsent(tech, k -> new HashMap<>())
                    .computeIfAbsent(currentMonth, k -> new int[2]);
            cell[0] = Math.max(cell[0], count);
        });

        return buildRows(data, rankByDemand(snapshot), currentMonth);
    }

    /** Buckets raw (tech, rawDate) samples into per-month cells; {@code index} selects job (0) vs. article (1). */
    private static void bucket(Map<String, Map<YearMonth, int[]>> data, List<TechDateSample> samples, int index) {
        for (TechDateSample sample : samples) {
            YearMonth ym = FlexibleDateParser.parseYearMonth(sample.rawDate());
            if (sample.tech() == null || ym == null) {
                continue;
            }
            int[] cell = data.computeIfAbsent(sample.tech(), k -> new HashMap<>())
                    .computeIfAbsent(ym, k -> new int[2]);
            cell[index] += 1;
        }
    }

    /** Ranks technologies by current demand (descending); 1 is the highest-demand technology. */
    private static Map<String, Integer> rankByDemand(Map<String, Integer> snapshot) {
        Map<String, Integer> rankByTech = new HashMap<>();
        List<Map.Entry<String, Integer>> ranked = new ArrayList<>(snapshot.entrySet());
        ranked.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        for (int i = 0; i < ranked.size(); i++) {
            rankByTech.put(ranked.get(i).getKey(), i + 1);
        }
        return rankByTech;
    }

    private static List<TechAnalyticsRow> buildRows(Map<String, Map<YearMonth, int[]>> data,
                                                      Map<String, Integer> rankByTech, YearMonth current) {
        List<TechAnalyticsRow> rows = new ArrayList<>();
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

                rows.add(new TechAnalyticsRow(tech, ym.atDay(1), job, article, mom, yoy, mom, rank));
            }
        }
        return rows;
    }

    /** Percentage growth of {@code current} vs {@code previous}; 0 when previous is missing/zero. */
    static double growth(int current, Integer previous) {
        if (previous == null || previous == 0) {
            return 0.0;
        }
        return Math.round(((current - previous) / (double) previous) * 10000.0) / 100.0;
    }
}
