package com.techpulse.techradar.features.compare.ports;

import com.techpulse.techradar.features.compare.domain.TechComparison;
import reactor.core.publisher.Mono;

/**
 * Output port for Python AI LLM summary service.
 */
public interface LlmSummaryPort {
    Mono<String> generateSummary(TechComparison comparison);
}
