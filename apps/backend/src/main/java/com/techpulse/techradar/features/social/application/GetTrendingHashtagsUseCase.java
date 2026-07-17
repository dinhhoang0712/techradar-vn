package com.techpulse.techradar.features.social.application;

import com.techpulse.techradar.features.social.ports.HashtagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class GetTrendingHashtagsUseCase {

    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 50;
    private static final int TRENDING_WINDOW_DAYS = 7;

    private final HashtagRepository hashtagRepository;

    public Flux<HashtagRepository.TrendingRow> execute(int limit) {
        int effectiveLimit = limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
        LocalDateTime since = LocalDateTime.now().minusDays(TRENDING_WINDOW_DAYS);
        return hashtagRepository.trending(since, effectiveLimit);
    }
}
