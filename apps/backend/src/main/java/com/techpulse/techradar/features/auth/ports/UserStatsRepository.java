package com.techpulse.techradar.features.auth.ports;

import reactor.core.publisher.Mono;

/**
 * Admin/analytics-only aggregate count over users, split out of {@link UserRepository} so
 * callers that only need a total user count (e.g. {@code GetPublicStatsUseCase}) don't have to
 * depend on the full per-request user CRUD surface just to read one count.
 */
public interface UserStatsRepository {

    /** Cheap {@code COUNT(*)} — unlike {@link UserRepository#findAll()} this never pulls row data. */
    Mono<Long> countAll();
}
