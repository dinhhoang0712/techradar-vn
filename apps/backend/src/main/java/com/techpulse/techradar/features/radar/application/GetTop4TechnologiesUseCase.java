package com.techpulse.techradar.features.radar.application;

import com.techpulse.techradar.features.radar.domain.RadarTrend;
import com.techpulse.techradar.features.radar.ports.RadarRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/**
 * Get top 4 technologies use case.
 */
@Component
@RequiredArgsConstructor
public class GetTop4TechnologiesUseCase {

    private final RadarRepository radarRepository;

    public Flux<RadarTrend> execute() {
        return radarRepository.findTopTechnologies(4);
    }
}
