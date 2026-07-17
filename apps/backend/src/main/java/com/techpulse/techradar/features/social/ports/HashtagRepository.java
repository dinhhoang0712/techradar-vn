package com.techpulse.techradar.features.social.ports;

import reactor.core.publisher.Flux;

import java.time.LocalDateTime;

public interface HashtagRepository {

    /** Most-used hashtags across posts created since {@code since}, most-used first. */
    Flux<TrendingRow> trending(LocalDateTime since, int limit);

    record TrendingRow(String tag, long postCount) {
    }
}
