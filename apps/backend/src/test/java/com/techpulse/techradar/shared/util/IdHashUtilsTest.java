package com.techpulse.techradar.shared.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IdHashUtilsTest {

    @Test
    void md5_isDeterministic_forTheSameInput() {
        assertThat(IdHashUtils.md5("https://example.com/article-1"))
                .isEqualTo(IdHashUtils.md5("https://example.com/article-1"));
    }

    @Test
    void md5_producesA32CharHexDigest() {
        String digest = IdHashUtils.md5("https://example.com/article-1");

        assertThat(digest).hasSize(32).matches("[0-9a-f]{32}");
    }

    @Test
    void md5_differsForDifferentInputs() {
        assertThat(IdHashUtils.md5("a")).isNotEqualTo(IdHashUtils.md5("b"));
    }

    @Test
    void md5_treatsNullAsEmptyString() {
        assertThat(IdHashUtils.md5(null)).isEqualTo(IdHashUtils.md5(""));
    }
}
