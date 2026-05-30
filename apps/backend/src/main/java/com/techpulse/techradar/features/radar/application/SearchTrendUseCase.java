package com.techpulse.techradar.features.radar.application;

import com.techpulse.techradar.features.radar.domain.RadarTrend;
import com.techpulse.techradar.features.radar.ports.RadarRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/**
 * Search trends use case.
 */
@Component
@RequiredArgsConstructor
public class SearchTrendUseCase {

    private final RadarRepository radarRepository;

    public Flux<RadarTrend> execute(String keyword, int limit) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return Flux.empty();
        }
        return radarRepository.searchTrends(keyword, limit);
    }
}
