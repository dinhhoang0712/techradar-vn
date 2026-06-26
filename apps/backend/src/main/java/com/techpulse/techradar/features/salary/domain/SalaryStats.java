package com.techpulse.techradar.features.salary.domain;

import java.util.Collections;
import java.util.List;

/**
 * Computes descriptive statistics over a list of salary midpoints (triệu VND).
 */
public final class SalaryStats {

    private SalaryStats() {}

    public record Stats(double median, double avg, double min, double max, double p25, double p75) {}

    public static Stats compute(List<Double> values) {
        if (values == null || values.isEmpty()) {
            return new Stats(0, 0, 0, 0, 0, 0);
        }
        List<Double> sorted = values.stream().sorted().toList();
        int n = sorted.size();
        return new Stats(
                percentile(sorted, 50),
                sorted.stream().mapToDouble(Double::doubleValue).average().orElse(0),
                sorted.get(0),
                sorted.get(n - 1),
                percentile(sorted, 25),
                percentile(sorted, 75)
        );
    }

    private static double percentile(List<Double> sorted, int p) {
        if (sorted.size() == 1) return sorted.get(0);
        double idx = (p / 100.0) * (sorted.size() - 1);
        int lo = (int) idx;
        int hi = Math.min(lo + 1, sorted.size() - 1);
        double frac = idx - lo;
        return sorted.get(lo) * (1 - frac) + sorted.get(hi) * frac;
    }
}