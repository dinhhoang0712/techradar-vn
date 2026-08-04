package com.techpulse.techradar.features.kafka.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LevelNormalizerTest {

    @Test
    void normalize_mapsEachBucketFromEnglishKeyword() {
        assertThat(LevelNormalizer.normalize("Senior Backend Developer")).isEqualTo("Senior");
        assertThat(LevelNormalizer.normalize("Middle Java Developer")).isEqualTo("Middle");
        assertThat(LevelNormalizer.normalize("Junior QA Engineer")).isEqualTo("Junior");
        assertThat(LevelNormalizer.normalize("Fresher - Entry Level")).isEqualTo("Fresher");
        assertThat(LevelNormalizer.normalize("Summer Intern")).isEqualTo("Intern");
        assertThat(LevelNormalizer.normalize("Engineering Manager")).isEqualTo("Lead");
    }

    @Test
    void normalize_mapsEachBucketFromVietnameseKeyword() {
        assertThat(LevelNormalizer.normalize("Nhân viên kinh doanh")).isEqualTo("Junior");
        assertThat(LevelNormalizer.normalize("Trưởng nhóm Backend")).isEqualTo("Lead");
        assertThat(LevelNormalizer.normalize("Mới tốt nghiệp")).isEqualTo("Fresher");
        assertThat(LevelNormalizer.normalize("Thực tập sinh IT")).isEqualTo("Intern");
        assertThat(LevelNormalizer.normalize("Chuyên gia dữ liệu")).isEqualTo("Senior");
    }

    @Test
    void normalize_prefersMostSeniorMatchOnTie() {
        assertThat(LevelNormalizer.normalize("Senior Team Lead")).isEqualTo("Lead");
    }

    @Test
    void normalize_returnsNullForBlankOrNull() {
        assertThat(LevelNormalizer.normalize("")).isNull();
        assertThat(LevelNormalizer.normalize(null)).isNull();
        assertThat(LevelNormalizer.normalize("   ")).isNull();
    }

    @Test
    void normalize_returnsNullWhenNoKeywordMatches() {
        assertThat(LevelNormalizer.normalize("Full Stack Developer")).isNull();
    }
}
