package com.techpulse.techradar.features.compare.application;

import com.techpulse.techradar.features.compare.domain.TechComparison;
import com.techpulse.techradar.features.compare.ports.CompareRepository;
import com.techpulse.techradar.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Compare technologies use case.
 */
@Component
@RequiredArgsConstructor
public class CompareTechnologiesUseCase {

    private final CompareRepository compareRepository;

    public Mono<TechComparison> execute(String tech1, String tech2) {
        if (tech1 == null || tech2 == null || tech1.equals(tech2)) {
            return Mono.error(
                    new IllegalArgumentException("Technologies must be different and not null")
            );
        }

        return compareRepository.compareTechnologies(tech1, tech2)
                .switchIfEmpty(Mono.error(
                        new NotFoundException("One or both technologies not found")
                ));
    }
}
