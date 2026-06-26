package com.techpulse.techradar.features.compare.application;

import com.techpulse.techradar.features.radar.domain.MonthlyCount;
import com.techpulse.techradar.features.radar.ports.RadarQueryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Build per-technology monthly comparison series from the shared analytics store.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CompareSearchUseCase {

    private final RadarQueryRepository radarQueryRepository;

    public Mono<List<MonthlyCount>> execute(List<String> keywords, int months) {
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
                .doOnError(e -> log.error("Compare search failed for keywords={}", cleaned, e));
    }
}
