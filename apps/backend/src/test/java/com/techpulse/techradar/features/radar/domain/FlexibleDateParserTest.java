package com.techpulse.techradar.features.radar.domain;

import org.junit.jupiter.api.Test;

import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;

class FlexibleDateParserTest {

    @Test
    void parseYearMonth_returnsNullForNull() {
        assertThat(FlexibleDateParser.parseYearMonth(null)).isNull();
    }

    @Test
    void parseYearMonth_parsesIsoDateAndDateTime() {
        assertThat(FlexibleDateParser.parseYearMonth("2026-06-30")).isEqualTo(YearMonth.of(2026, 6));
        assertThat(FlexibleDateParser.parseYearMonth("2026-06-30T10:15:00")).isEqualTo(YearMonth.of(2026, 6));
    }

    @Test
    void parseYearMonth_parsesUnambiguousDdMmYyyy() {
        // day (29) > 12 unambiguously marks the day position → 29/06/2026 is dd/MM/yyyy.
        assertThat(FlexibleDateParser.parseYearMonth("29/06/2026")).isEqualTo(YearMonth.of(2026, 6));
    }

    @Test
    void parseYearMonth_parsesUnambiguousMmDdYyyy() {
        // day (30) > 12 in the second slot unambiguously marks 06/30/2026 as MM/dd/yyyy.
        assertThat(FlexibleDateParser.parseYearMonth("06/30/2026")).isEqualTo(YearMonth.of(2026, 6));
    }

    @Test
    void parseYearMonth_assumesDdMmYyyyWhenAmbiguous() {
        // Both groups <= 12: genuinely ambiguous, defaults to dd/MM/yyyy (VN convention).
        assertThat(FlexibleDateParser.parseYearMonth("05/06/2026")).isEqualTo(YearMonth.of(2026, 6));
    }

    @Test
    void parseYearMonth_returnsNullWhenBothSlashGroupsExceedTwelve() {
        assertThat(FlexibleDateParser.parseYearMonth("29/30/2026")).isNull();
    }

    @Test
    void parseYearMonth_returnsNullForUnrecognizedFormat() {
        assertThat(FlexibleDateParser.parseYearMonth("not a date")).isNull();
        assertThat(FlexibleDateParser.parseYearMonth("")).isNull();
    }

    @Test
    void parseYearMonth_returnsNullForOutOfRangeIsoMonth() {
        assertThat(FlexibleDateParser.parseYearMonth("2026-13-01")).isNull();
    }
}
