package com.techpulse.techradar.features.messaging.ports;

import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

/**
 * Admin/analytics-only aggregate counts over messaging data, split out of
 * {@link ConversationRepository} so callers that only report dashboard metrics (e.g.
 * {@code MessagingMetricsService}) don't have to depend on the full per-request conversation CRUD
 * surface just to read three counts.
 */
public interface MessagingStatsRepository {

    Mono<Long> countConversations();

    Mono<Long> countMessages();

    Mono<Long> countMessagesSince(LocalDateTime since);
}
