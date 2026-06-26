package com.techpulse.techradar.features.salary.domain;

/**
 * Parsed salary range normalized to triệu VND.
 * null means the raw salary string could not be parsed (e.g. "Thỏa thuận").
 */
public record SalaryRange(double minVnd, double maxVnd) {

    public double midpoint() {
        return (minVnd + maxVnd) / 2.0;
    }
}