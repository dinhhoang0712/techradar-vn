package com.techpulse.techradar.features.radar.domain;

import java.time.DateTimeException;
import java.time.YearMonth;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses job/article date strings pulled from Neo4j into a {@link YearMonth}, tolerating the
 * mixed formats produced by different crawler sources.
 * <p>
 * Handled formats:
 *   "2026-06-30"         → ISO, unambiguous
 *   "29/06/2026"         → dd/MM/yyyy (day &gt; 12 marks the day position)
 *   "06/30/2026"         → MM/dd/yyyy (day &gt; 12 marks the day position)
 *   "05/06/2026"         → ambiguous (both groups &le; 12) → assumed dd/MM/yyyy,
 *                          the convention used by the Vietnamese job sites this pipeline crawls
 *                          (ITviec, TopCV).
 */
public final class FlexibleDateParser {

    private FlexibleDateParser() {}

    private static final Pattern ISO_DATE = Pattern.compile("^(\\d{4})-(\\d{2})");
    private static final Pattern SLASH_DATE = Pattern.compile("^(\\d{2})/(\\d{2})/(\\d{4})");

    public static YearMonth parseYearMonth(String raw) {
        if (raw == null) {
            return null;
        }
        Matcher iso = ISO_DATE.matcher(raw);
        if (iso.find()) {
            return toYearMonth(iso.group(1), iso.group(2));
        }
        Matcher slash = SLASH_DATE.matcher(raw);
        if (slash.find()) {
            int first = Integer.parseInt(slash.group(1));
            int second = Integer.parseInt(slash.group(2));
            if (first > 12 && second <= 12) {
                return toYearMonth(slash.group(3), slash.group(2)); // dd/MM/yyyy
            }
            if (second > 12 && first <= 12) {
                return toYearMonth(slash.group(3), slash.group(1)); // MM/dd/yyyy
            }
            if (first <= 12 && second <= 12) {
                return toYearMonth(slash.group(3), slash.group(2)); // ambiguous → dd/MM/yyyy
            }
            return null; // both > 12: not a valid date either way
        }
        return null;
    }

    private static YearMonth toYearMonth(String year, String month) {
        try {
            return YearMonth.of(Integer.parseInt(year), Integer.parseInt(month));
        } catch (NumberFormatException | DateTimeException e) {
            return null;
        }
    }
}
