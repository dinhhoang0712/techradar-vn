package com.techpulse.techradar.features.kgreview.application;

import com.techpulse.techradar.features.kgreview.ports.TechAliasReviewRepository;
import com.techpulse.techradar.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class RejectTechAliasUseCase {

    private final TechAliasReviewRepository repository;

    public Mono<Void> execute(long id) {
        return repository.markRejected(id)
                .flatMap(rejected -> rejected
                        ? Mono.<Void>empty()
                        : Mono.error(new NotFoundException("Pending review item not found: " + id)));
    }
}
