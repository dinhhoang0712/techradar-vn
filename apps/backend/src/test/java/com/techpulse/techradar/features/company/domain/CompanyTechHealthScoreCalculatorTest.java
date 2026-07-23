package com.techpulse.techradar.features.company.domain;

import com.techpulse.techradar.features.radar.domain.TechSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CompanyTechHealthScoreCalculatorTest {

    private static TechSnapshot snapshot(String name, double growthRate) {
        return new TechSnapshot(name, 100, growthRate, growthRate, 100);
    }

    @Test
    void compute_withNoTrackedTechnologies_isUnavailable() {
        CompanyTechHealthScore result = CompanyTechHealthScoreCalculator.compute(4, List.of());

        assertThat(result.available()).isFalse();
        assertThat(result.score()).isZero();
        assertThat(result.label()).isEqualTo("Chưa đủ dữ liệu để đánh giá");
        assertThat(result.stackSize()).isEqualTo(4);
        assertThat(result.trackedCount()).isZero();
        assertThat(result.strengths()).isEmpty();
        assertThat(result.watchOuts()).isEmpty();
    }

    @Test
    void compute_withFlatGrowthAcrossAllTech_scoresNeutral50() {
        CompanyTechHealthScore result = CompanyTechHealthScoreCalculator.compute(2,
                List.of(snapshot("Java", 0), snapshot("Postgres", 0)));

        assertThat(result.available()).isTrue();
        assertThat(result.score()).isEqualTo(50);
        assertThat(result.label()).isEqualTo("Ổn định");
        assertThat(result.strengths()).isEmpty();
        assertThat(result.watchOuts()).isEmpty();
    }

    @Test
    void compute_withStrongPositiveGrowth_clampsAt100AndLabelsUp() {
        CompanyTechHealthScore result = CompanyTechHealthScoreCalculator.compute(1,
                List.of(snapshot("Rust", 500)));

        assertThat(result.score()).isEqualTo(100);
        assertThat(result.label()).isEqualTo("Đang bắt kịp xu hướng công nghệ");
        assertThat(result.strengths()).containsExactly("Rust");
        assertThat(result.watchOuts()).isEmpty();
    }

    @Test
    void compute_withStrongNegativeGrowth_clampsAt0AndLabelsDown() {
        CompanyTechHealthScore result = CompanyTechHealthScoreCalculator.compute(1,
                List.of(snapshot("Flash", -500)));

        assertThat(result.score()).isEqualTo(0);
        assertThat(result.label()).isEqualTo("Có dấu hiệu dùng công nghệ đang suy giảm nhu cầu");
        assertThat(result.strengths()).isEmpty();
        assertThat(result.watchOuts()).containsExactly("Flash");
    }

    @Test
    void compute_ranksStrengthsDescendingAndWatchOutsAscending_cappedAtThree() {
        CompanyTechHealthScore result = CompanyTechHealthScoreCalculator.compute(6, List.of(
                snapshot("A", 100),  // score 100
                snapshot("B", 60),   // score 80
                snapshot("C", 30),   // score 65
                snapshot("D", -30),  // score 35
                snapshot("E", -60),  // score 20
                snapshot("F", -100)  // score 0
        ));

        assertThat(result.strengths()).containsExactly("A", "B", "C");
        assertThat(result.watchOuts()).containsExactly("F", "E", "D");
        assertThat(result.trackedCount()).isEqualTo(6);
        assertThat(result.stackSize()).isEqualTo(6);
    }

    @Test
    void compute_averagesOnlyTrackedTechnologies_notFullStackSize() {
        // stackSize is 5 (company has 5 technologies) but only 1 has tech_analytics data.
        CompanyTechHealthScore result = CompanyTechHealthScoreCalculator.compute(5,
                List.of(snapshot("Kubernetes", 100)));

        assertThat(result.available()).isTrue();
        assertThat(result.score()).isEqualTo(100);
        assertThat(result.stackSize()).isEqualTo(5);
        assertThat(result.trackedCount()).isEqualTo(1);
    }
}
