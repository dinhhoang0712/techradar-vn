package com.techpulse.techradar.features.kgreview.application;

import com.techpulse.techradar.features.kgreview.domain.TechAliasReviewItem;
import com.techpulse.techradar.features.kgreview.ports.TechAliasReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class ListPendingTechAliasesUseCase {

    private final TechAliasReviewRepository repository;

    public Flux<TechAliasReviewItem> execute(int limit, int offset) {
        return repository.findPending(limit, offset);
    }

    /** Cheap count for the admin sidebar badge — no LLM/Neo4j call, plain Postgres COUNT(*). */
    public Mono<Long> count() {
        return repository.countPending();
    }
}
