package com.techpulse.techradar.features.company.domain;

import com.techpulse.techradar.features.radar.domain.TechSnapshot;

import java.util.Comparator;
import java.util.List;

/**
 * Pure scoring math for {@link CompanyTechHealthScore} — no I/O, unit-testable by constructing
 * {@link TechSnapshot} lists directly. Mirrors the isolation pattern used by
 * {@code TechAnalyticsTransformer} for the radar feature's growth math.
 * <p>
 * Per-technology score is {@code clamp(50 + growth_rate / 2, 0, 100)}: flat growth stays neutral
 * (50), and the score only moves toward the extremes as month-over-month demand growth swings
 * strongly positive or negative. The company score averages this over only the technologies that
 * have a tracked {@code tech_analytics} row — untracked niche/internal tools are excluded rather
 * than penalized.
 */
public final class CompanyTechHealthScoreCalculator {

    private static final int MAX_HIGHLIGHTS = 3;
    private static final double STRENGTH_THRESHOLD = 60;
    private static final double WATCH_OUT_THRESHOLD = 40;

    private CompanyTechHealthScoreCalculator() {
    }

    public static CompanyTechHealthScore compute(int stackSize, List<TechSnapshot> tracked) {
        if (tracked.isEmpty()) {
            return new CompanyTechHealthScore(
                    false, 0, "Chưa đủ dữ liệu để đánh giá", stackSize, 0, List.of(), List.of());
        }

        List<ScoredTech> scored = tracked.stream()
                .map(s -> new ScoredTech(s.name(), clamp(50 + s.growthRate() / 2)))
                .toList();

        int score = (int) Math.round(scored.stream().mapToDouble(ScoredTech::score).average().orElse(50));

        List<String> strengths = scored.stream()
                .filter(s -> s.score() >= STRENGTH_THRESHOLD)
                .sorted(Comparator.comparingDouble(ScoredTech::score).reversed())
                .limit(MAX_HIGHLIGHTS)
                .map(ScoredTech::name)
                .toList();

        List<String> watchOuts = scored.stream()
                .filter(s -> s.score() < WATCH_OUT_THRESHOLD)
                .sorted(Comparator.comparingDouble(ScoredTech::score))
                .limit(MAX_HIGHLIGHTS)
                .map(ScoredTech::name)
                .toList();

        return new CompanyTechHealthScore(true, score, labelFor(score), stackSize, tracked.size(), strengths, watchOuts);
    }

    private static String labelFor(int score) {
        if (score >= 70) {
            return "Đang bắt kịp xu hướng công nghệ";
        }
        if (score >= 45) {
            return "Ổn định";
        }
        return "Có dấu hiệu dùng công nghệ đang suy giảm nhu cầu";
    }

    private static double clamp(double value) {
        return Math.max(0, Math.min(100, value));
    }

    private record ScoredTech(String name, double score) {
    }
}
