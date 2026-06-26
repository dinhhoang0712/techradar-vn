package com.techpulse.techradar.features.salary.domain;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses Vietnamese/English free-text salary strings into numeric SalaryRange (triệu VND).
 *
 * Handled formats:
 *   "10 - 20 triệu"             → 10–20 triệu VND
 *   "Trên 15 triệu"             → 15–30 triệu VND (assumed cap = 2×min)
 *   "Lên đến 30 triệu"          → 15–30 triệu VND (assumed floor = 0.5×max)
 *   "1000 - 2000 USD"           → 25–50 triệu VND  (1 USD = 25,000 VND)
 *   "$1,500 - $3,000"           → 37.5–75 triệu VND
 *   "Thỏa thuận" / "Negotiate"  → null (unparseable)
 *
 * All values are clamped to [1, 500] triệu VND; anything outside is discarded as noise.
 */
public final class SalaryParser {

    private SalaryParser() {}

    // 1 USD ≈ 25,000 VND  →  1 USD = 0.025 triệu VND
    private static final double USD_TO_M_VND = 0.025;

    // Recognise the common "skip" keywords
    private static final Pattern SKIP = Pattern.compile(
            "(?i)(th[oỏ]a\\s*thu[aậ]n|negotiate|negotiable|competitive|c[aạ]nh\\s*tranh" +
            "|agreement|up\\s*to\\s*you|discuss|kh[oô]ng\\s*hi[eệ]n\\s*th[iị])",
            Pattern.UNICODE_CASE);

    // "10 - 20"  or  "10-20"  (numbers may have commas/dots as thousands separator)
    private static final Pattern RANGE = Pattern.compile(
            "([\\d][\\d,\\.]*)" +           // lower bound
            "\\s*[-–—~]\\s*" +              // separator
            "([\\d][\\d,\\.]*)",            // upper bound
            Pattern.UNICODE_CASE);

    // Single value with modifier: "trên 15", "trên 20", "lên đến 30", "tới 30"
    private static final Pattern ABOVE = Pattern.compile(
            "(?i)(tr[eê]n|above|over|from)\\s+" +
            "([\\d][\\d,\\.]*)",
            Pattern.UNICODE_CASE);

    private static final Pattern UPTO = Pattern.compile(
            "(?i)(l[eê]n\\s*[đd][eế]n|t[oớ]i|up\\s*to|max(?:imum)?|t[oố]i\\s*[đd]a)\\s+" +
            "([\\d][\\d,\\.]*)",
            Pattern.UNICODE_CASE);

    // Currency detection
    private static final Pattern USD_MARKER = Pattern.compile(
            "(?i)(\\$|usd|dollar)", Pattern.UNICODE_CASE);

    private static final double MIN_VALID = 1.0;
    private static final double MAX_VALID = 500.0;

    public static Optional<SalaryRange> parse(String raw) {
        if (raw == null || raw.isBlank()) return Optional.empty();
        if (SKIP.matcher(raw).find()) return Optional.empty();

        boolean isUsd = USD_MARKER.matcher(raw).find();

        // Try range first
        Matcher m = RANGE.matcher(raw);
        if (m.find()) {
            double lo = toDouble(m.group(1));
            double hi = toDouble(m.group(2));
            if (lo > hi) { double t = lo; lo = hi; hi = t; }
            return toRange(lo, hi, isUsd, raw);
        }

        // "above / trên X"
        Matcher above = ABOVE.matcher(raw);
        if (above.find()) {
            double lo = toDouble(above.group(2));
            return toRange(lo, lo * 2.0, isUsd, raw);
        }

        // "up to / lên đến X"
        Matcher upto = UPTO.matcher(raw);
        if (upto.find()) {
            double hi = toDouble(upto.group(2));
            return toRange(hi * 0.5, hi, isUsd, raw);
        }

        return Optional.empty();
    }

    private static Optional<SalaryRange> toRange(double lo, double hi, boolean isUsd, String raw) {
        if (lo <= 0 || hi <= 0) return Optional.empty();
        double loVnd = isUsd ? usdToMVnd(lo) : heuristicToMVnd(lo, raw);
        double hiVnd = isUsd ? usdToMVnd(hi) : heuristicToMVnd(hi, raw);
        if (loVnd < MIN_VALID || hiVnd > MAX_VALID) return Optional.empty();
        return Optional.of(new SalaryRange(loVnd, hiVnd));
    }

    /**
     * Figures out the VND unit when no explicit currency is stated.
     * - If "triệu" or "million" is present → already in triệu VND
     * - If number is small (≤ 200) → assume triệu VND (common Vietnamese range)
     * - If number is large (> 1000) → assume raw VND thousands → convert
     * - Otherwise treat as triệu VND
     */
    private static double heuristicToMVnd(double value, String raw) {
        String lower = raw.toLowerCase();
        if (lower.contains("triệu") || lower.contains("million") || lower.contains("tr ")) {
            return value;
        }
        // Large raw number without unit → likely "nghìn VND" (thousands VND)
        if (value > 1000) {
            return value / 1000.0;
        }
        // Small number → treat as triệu VND
        return value;
    }

    private static double usdToMVnd(double usd) {
        return usd * USD_TO_M_VND;
    }

    private static double toDouble(String s) {
        try {
            return Double.parseDouble(s.replace(",", "").replace(".", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
