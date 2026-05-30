package com.techpulse.techradar.features.auth.ports;

import com.techpulse.techradar.features.auth.domain.User;
import reactor.core.publisher.Mono;

/**
 * Output port for user repository.
 * Domain interface for persistence abstraction.
 */
public interface UserRepository {
    Mono<User> findByEmail(String email);

    Mono<User> findById(String userId);

    Mono<User> save(User user);

    Mono<Boolean> existsByEmail(String email);
}
