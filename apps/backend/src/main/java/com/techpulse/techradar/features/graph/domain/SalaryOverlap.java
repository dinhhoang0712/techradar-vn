package com.techpulse.techradar.features.graph.domain;

import java.util.regex.Pattern;

/**
 * Best-effort overlap check against a node's free-text {@code salary} property
 * (e.g. "15-25 triệu", "20 triệu", "Thoả thuận"). Only meaningful for nodes that actually
 * carry a salary value (i.e. Job nodes) — nodes without one (Company, Skill, ...) should be
 * left unaffected by this filter. If no filter is requested, or the salary text has no
 * parseable number, the node passes through.
 */
public final class SalaryOverlap {

    private SalaryOverlap() {
    }

    private static final Pattern SALARY_NUMBER = Pattern.compile("\\d+");

    public static boolean matches(Object rawSalary, Integer minSalary, Integer maxSalary) {
        if (minSalary == null && maxSalary == null) {
            return true;
        }
        if (rawSalary == null) {
            return true;
        }
        var matcher = SALARY_NUMBER.matcher(String.valueOf(rawSalary));
        int lo = Integer.MAX_VALUE;
        int hi = Integer.MIN_VALUE;
        while (matcher.find()) {
            int value = Integer.parseInt(matcher.group());
            lo = Math.min(lo, value);
            hi = Math.max(hi, value);
        }
        if (lo > hi) {
            // Unparseable (e.g. "Thoả thuận") — can't confirm it's in range.
            return false;
        }
        if (minSalary != null && hi < minSalary) {
            return false;
        }
        return maxSalary == null || lo <= maxSalary;
    }
}
