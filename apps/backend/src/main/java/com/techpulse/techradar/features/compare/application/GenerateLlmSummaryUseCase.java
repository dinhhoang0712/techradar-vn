package com.techpulse.techradar.features.compare.application;

import com.techpulse.techradar.features.compare.domain.TechComparison;
import com.techpulse.techradar.features.compare.ports.LlmSummaryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Generate LLM comparison summary use case.
 */
@Component
@RequiredArgsConstructor
public class GenerateLlmSummaryUseCase {

    private final LlmSummaryPort llmSummaryPort;

    public Mono<String> execute(TechComparison comparison) {
        return llmSummaryPort.generateSummary(comparison);
    }
}
