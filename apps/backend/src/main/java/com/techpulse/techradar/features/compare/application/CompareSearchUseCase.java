package com.techpulse.techradar.features.compare.application;

import com.techpulse.techradar.features.compare.domain.TechComparisonSeries;
import com.techpulse.techradar.features.radar.domain.MonthlyCount;
import com.techpulse.techradar.features.radar.ports.RadarQueryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Build per-technology monthly comparison series from the shared analytics store.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CompareSearchUseCase {

    private final RadarQueryRepository radarQueryRepository;

    public Mono<List<TechComparisonSeries>> execute(List<String> keywords, int months) {
        if (keywords == null || keywords.isEmpty()) {
            log.warn("Compare search skipped: no keywords provided");
            return Mono.just(List.of());
        }
        int window = months <= 0 ? 12 : Math.min(months, 60);
        List<String> cleaned = keywords.stream()
                .filter(k -> k != null && !k.isBlank())
                .map(String::trim)
                .toList();
        if (cleaned.isEmpty()) {
            log.warn("Compare search skipped: keywords were all blank");
            return Mono.just(List.of());
        }
        log.info("Comparing monthly series for keywords={} window={}m", cleaned, window);
        return radarQueryRepository.monthlySeries(cleaned, window).collectList()
                .doOnSuccess(series -> log.info("Compare search produced {} monthly data points for keywords={}",
                        series.size(), cleaned))
                .doOnError(e -> log.error("Compare search failed for keywords={}", cleaned, e))
                .map(CompareSearchUseCase::groupByTechnology);
    }

    /**
     * Groups monthly rows by technology, carrying the latest yoy/mom/growth rate. Rows are
     * ordered ascending by {@code radarQueryRepository.monthlySeries}, so the last row collected
     * per technology reflects its most recent month.
     */
    private static List<TechComparisonSeries> groupByTechnology(List<MonthlyCount> rows) {
        Map<String, List<MonthlyCount>> byTech = new LinkedHashMap<>();
        for (MonthlyCount row : rows) {
            byTech.computeIfAbsent(row.name(), k -> new ArrayList<>()).add(row);
        }
        return byTech.values().stream()
                .map(monthly -> {
                    MonthlyCount latest = monthly.get(monthly.size() - 1);
                    return new TechComparisonSeries(
                            latest.name(), latest.yoyRate(), latest.momRate(), latest.growthRate(), monthly);
                })
                .toList();
    }
}
