package com.techpulse.techradar.features.auth.application;

import com.techpulse.techradar.features.auth.domain.User;
import com.techpulse.techradar.features.auth.ports.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Resolves the currently authenticated user from its id (taken from the JWT principal).
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class GetCurrentUserUseCase {

    private final UserRepository userRepository;

    public Mono<User> execute(String userId) {
        if (userId == null || userId.isBlank()) {
            log.warn("Resolve current user skipped: missing or blank userId");
            return Mono.empty();
        }
        return userRepository.findById(userId)
                .switchIfEmpty(Mono.<User>empty()
                        .doOnSubscribe(s -> log.warn("Current user not found for userId={}", userId)))
                .doOnSuccess(user -> {
                    if (user != null) {
                        log.info("Resolved current user userId={}", userId);
                    }
                });
    }
}
