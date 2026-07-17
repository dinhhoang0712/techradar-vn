package com.techpulse.techradar.features.salary.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SalaryParserTest {

    @Test
    void parse_returnsEmptyForNullOrBlank() {
        assertThat(SalaryParser.parse(null)).isEmpty();
        assertThat(SalaryParser.parse("")).isEmpty();
        assertThat(SalaryParser.parse("   ")).isEmpty();
    }

    @Test
    void parse_returnsEmptyForNegotiableKeywords() {
        assertThat(SalaryParser.parse("Thỏa thuận")).isEmpty();
        assertThat(SalaryParser.parse("Negotiable")).isEmpty();
        assertThat(SalaryParser.parse("Competitive salary")).isEmpty();
        assertThat(SalaryParser.parse("Cạnh tranh")).isEmpty();
        assertThat(SalaryParser.parse("Up to you")).isEmpty();
    }

    @Test
    void parse_parsesSimpleVndRange() {
        assertThat(SalaryParser.parse("10 - 20 triệu")).contains(new SalaryRange(10.0, 20.0));
    }

    @Test
    void parse_parsesUsdRange() {
        assertThat(SalaryParser.parse("1000 - 2000 USD")).contains(new SalaryRange(25.0, 50.0));
    }

    @Test
    void parse_parsesDollarSignRangeWithThousandsSeparators() {
        assertThat(SalaryParser.parse("$1,500 - $3,000")).contains(new SalaryRange(37.5, 75.0));
    }

    @Test
    void parse_parsesItviecStyleUsdRange() {
        // Exact format produced by ITviec.py's JSON-LD baseSalary extraction.
        assertThat(SalaryParser.parse("1,200 - 2,000 USD")).contains(new SalaryRange(30.0, 50.0));
    }

    @Test
    void parse_parsesAboveKeywordAsDoubleTheFloor() {
        assertThat(SalaryParser.parse("Trên 15 triệu")).contains(new SalaryRange(15.0, 30.0));
    }

    @Test
    void parse_parsesUpToKeywordAsHalfTheCeiling() {
        assertThat(SalaryParser.parse("Lên đến 30 triệu")).contains(new SalaryRange(15.0, 30.0));
    }

    @Test
    void parse_swapsBoundsWhenReversed() {
        assertThat(SalaryParser.parse("20 - 10 triệu")).contains(new SalaryRange(10.0, 20.0));
    }

    @Test
    void parse_treatsBareLargeNumbersAsThousandsOfVnd() {
        // No "triệu"/USD marker and values > 1000 → assumed to be raw VND thousands.
        assertThat(SalaryParser.parse("1500 - 2000")).contains(new SalaryRange(1.5, 2.0));
    }

    @Test
    void parse_treatsBareSmallNumbersAsTrieuVnd() {
        assertThat(SalaryParser.parse("10 - 20")).contains(new SalaryRange(10.0, 20.0));
    }

    @Test
    void parse_discardsValuesAboveMaxValid() {
        assertThat(SalaryParser.parse("600 - 700 triệu")).isEmpty();
    }

    @Test
    void parse_returnsEmptyForUnrecognizedFormat() {
        assertThat(SalaryParser.parse("call HR for details")).isEmpty();
    }

    @Test
    void midpoint_isTheAverageOfMinAndMax() {
        assertThat(new SalaryRange(10.0, 20.0).midpoint()).isEqualTo(15.0);
    }
}
