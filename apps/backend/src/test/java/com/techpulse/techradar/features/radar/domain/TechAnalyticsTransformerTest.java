package com.techpulse.techradar.features.radar.domain;

import org.junit.jupiter.api.Test;

import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Unit tests for the pure scoring/ranking transform, exercised entirely with in-memory
 * {@link TechDateSample}/{@link TechCount} fixtures — no Neo4j or Postgres involved, which is
 * the point: this math was previously reachable only by running the full ETL against a live
 * database.
 */
class TechAnalyticsTransformerTest {

    private static final YearMonth CURRENT = YearMonth.of(2026, 7);
    private static final YearMonth PREVIOUS = CURRENT.minusMonths(1); // 2026-06
    private static final YearMonth TWO_AGO = CURRENT.minusMonths(2);  // 2026-05

    @Test
    void compute_returnsEmptyWhenAllInputsEmpty() {
        List<TechAnalyticsRow> rows = TechAnalyticsTransformer.compute(List.of(), List.of(), List.of(), CURRENT);
        assertThat(rows).isEmpty();
    }

    @Test
    void compute_buildsOneRowPerTechMonthWithActivityFallbackToArticles() {
        List<TechDateSample> articles = List.of(new TechDateSample("Java", date(TWO_AGO)));
        List<TechDateSample> jobs = List.of(new TechDateSample("Java", date(PREVIOUS)));
        List<TechCount> snapshot = List.of();

        List<TechAnalyticsRow> rows = TechAnalyticsTransformer.compute(articles, jobs, snapshot, CURRENT);

        TechAnalyticsRow oldest = rowFor(rows, "Java", TWO_AGO);
        assertThat(oldest.jobCount()).isZero();
        assertThat(oldest.articleCount()).isEqualTo(1);
        // No job postings that month -> activity falls back to the article count -> mom growth vs.
        // an even-older month with no data is 0.
        assertThat(oldest.momGrowth()).isEqualTo(0.0);

        TechAnalyticsRow prev = rowFor(rows, "Java", PREVIOUS);
        assertThat(prev.jobCount()).isEqualTo(1);
        assertThat(prev.articleCount()).isZero();
        // activity(TWO_AGO) = 1 (article fallback), activity(PREVIOUS) = 1 (job count) -> 0% growth.
        assertThat(prev.momGrowth()).isEqualTo(0.0);
    }

    @Test
    void compute_foldsCurrentDemandSnapshotIntoCurrentMonthAndRanksByIt() {
        List<TechDateSample> jobs = List.of(
                new TechDateSample("Java", date(PREVIOUS)),
                new TechDateSample("Python", date(PREVIOUS)));
        List<TechCount> snapshot = List.of(new TechCount("Java", 20), new TechCount("Python", 5));

        List<TechAnalyticsRow> rows = TechAnalyticsTransformer.compute(List.of(), jobs, snapshot, CURRENT);

        TechAnalyticsRow javaCurrent = rowFor(rows, "Java", CURRENT);
        assertThat(javaCurrent.jobCount()).isEqualTo(20);
        assertThat(javaCurrent.ranking()).isEqualTo(1);
        // previous month activity = 1 (one job posting) -> mom = (20-1)/1*100 = 1900%.
        assertThat(javaCurrent.momGrowth()).isCloseTo(1900.0, within(0.01));

        TechAnalyticsRow pythonCurrent = rowFor(rows, "Python", CURRENT);
        assertThat(pythonCurrent.jobCount()).isEqualTo(5);
        assertThat(pythonCurrent.ranking()).isEqualTo(2);

        // Only the current month carries a ranking; historical rows never do.
        TechAnalyticsRow javaPrev = rowFor(rows, "Java", PREVIOUS);
        assertThat(javaPrev.ranking()).isNull();
    }

    @Test
    void compute_snapshotFoldKeepsExistingCountWhenItAlreadyExceedsSnapshot() {
        // Job postings dated in the current month already show 3; the snapshot (1) must not
        // clobber a count that's already higher (fold is a max(), not an overwrite).
        List<TechDateSample> jobs = List.of(
                new TechDateSample("Java", date(CURRENT)),
                new TechDateSample("Java", date(CURRENT)),
                new TechDateSample("Java", date(CURRENT)));
        List<TechCount> snapshot = List.of(new TechCount("Java", 1));

        List<TechAnalyticsRow> rows = TechAnalyticsTransformer.compute(List.of(), jobs, snapshot, CURRENT);

        TechAnalyticsRow row = rowFor(rows, "Java", CURRENT);
        assertThat(row.jobCount()).isEqualTo(3); // max(3 postings, snapshot 1) -> stays at 3
    }

    @Test
    void compute_addsTechThatOnlyExistsInTheSnapshotWithNoDateSamples() {
        List<TechCount> snapshot = List.of(new TechCount("Rust", 7));

        List<TechAnalyticsRow> rows = TechAnalyticsTransformer.compute(List.of(), List.of(), snapshot, CURRENT);

        TechAnalyticsRow row = rowFor(rows, "Rust", CURRENT);
        assertThat(row.jobCount()).isEqualTo(7);
        assertThat(row.ranking()).isEqualTo(1);
    }

    @Test
    void compute_ignoresSamplesAndSnapshotEntriesWithNullTech() {
        List<TechDateSample> jobs = List.of(new TechDateSample(null, date(CURRENT)));
        List<TechCount> snapshot = List.of(new TechCount(null, 99));

        List<TechAnalyticsRow> rows = TechAnalyticsTransformer.compute(List.of(), jobs, snapshot, CURRENT);

        assertThat(rows).isEmpty();
    }

    @Test
    void compute_skipsSamplesWithUnparsableDates() {
        List<TechDateSample> jobs = List.of(new TechDateSample("Java", "not-a-date"));

        List<TechAnalyticsRow> rows = TechAnalyticsTransformer.compute(List.of(), jobs, List.of(), CURRENT);

        assertThat(rows).isEmpty();
    }

    @Test
    void growth_isZeroWhenPreviousIsNullOrZero() {
        assertThat(TechAnalyticsTransformer.growth(10, null)).isEqualTo(0.0);
        assertThat(TechAnalyticsTransformer.growth(10, 0)).isEqualTo(0.0);
    }

    @Test
    void growth_computesRoundedPercentage() {
        assertThat(TechAnalyticsTransformer.growth(2, 1)).isEqualTo(100.0);
        assertThat(TechAnalyticsTransformer.growth(5, 3)).isEqualTo(66.67);
        assertThat(TechAnalyticsTransformer.growth(0, 4)).isEqualTo(-100.0);
    }

    private static String date(YearMonth ym) {
        return ym.atDay(10).toString(); // ISO yyyy-MM-dd, unambiguous for FlexibleDateParser.
    }

    private static TechAnalyticsRow rowFor(List<TechAnalyticsRow> rows, String tech, YearMonth month) {
        return rows.stream()
                .filter(r -> r.tech().equals(tech) && r.month().equals(month.atDay(1)))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No row for " + tech + "/" + month));
    }
}
