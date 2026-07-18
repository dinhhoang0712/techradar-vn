package com.techpulse.techradar.features.system.ports;

import reactor.core.publisher.Mono;

/**
 * Output port for the Python AI moderation-suggestion service.
 */
public interface ModerationSuggestionPort {

    Mono<Suggestion> suggest(String targetType, String targetContent, String reportReason);

    record Suggestion(String action, String reason, double confidence) {
    }
}
