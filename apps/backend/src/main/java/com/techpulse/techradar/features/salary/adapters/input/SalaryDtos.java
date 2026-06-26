package com.techpulse.techradar.features.salary.adapters.input;

import com.techpulse.techradar.features.salary.domain.SalaryInsight;
import lombok.Builder;
import lombok.Value;

import java.util.List;

public class SalaryDtos {

    @Value
    @Builder
    public static class SalaryInsightResponse {
        String techName;
        int totalJobs;
        int jobsWithSalary;
        double medianSalaryMVnd;
        double avgSalaryMVnd;
        double minSalaryMVnd;
        double maxSalaryMVnd;
        double p25SalaryMVnd;
        double p75SalaryMVnd;
        String salaryRange;
        List<String> topCoTechs;

        public static SalaryInsightResponse from(SalaryInsight insight) {
            return SalaryInsightResponse.builder()
                    .techName(insight.techName())
                    .totalJobs(insight.totalJobs())
                    .jobsWithSalary(insight.jobsWithSalary())
                    .medianSalaryMVnd(insight.medianVnd())
                    .avgSalaryMVnd(insight.avgVnd())
                    .minSalaryMVnd(insight.minVnd())
                    .maxSalaryMVnd(insight.maxVnd())
                    .p25SalaryMVnd(insight.p25Vnd())
                    .p75SalaryMVnd(insight.p75Vnd())
                    .salaryRange(formatRange(insight.p25Vnd(), insight.p75Vnd()))
                    .topCoTechs(insight.topCoTechs())
                    .build();
        }

        private static String formatRange(double p25, double p75) {
            if (p25 <= 0 && p75 <= 0) return "N/A";
            return String.format("%.0f - %.0f triệu VND", p25, p75);
        }
    }
}