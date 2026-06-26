package com.techpulse.techradar.features.compare.application;

import com.techpulse.techradar.features.compare.domain.TechComparison;
import com.techpulse.techradar.features.compare.ports.LlmSummaryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Generate LLM comparison summary use case.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GenerateLlmSummaryUseCase {

    private final LlmSummaryPort llmSummaryPort;

    public Mono<String> execute(TechComparison comparison) {
        log.info("Generating LLM comparison summary");
        return llmSummaryPort.generateSummary(comparison)
                .doOnSuccess(summary -> log.info("LLM comparison summary generated ({} chars)",
                        summary == null ? 0 : summary.length()))
                .doOnError(e -> log.error("LLM comparison summary generation failed", e));
    }
}
