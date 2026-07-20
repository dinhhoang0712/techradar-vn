package com.techpulse.techradar.features.user.application;

import com.techpulse.techradar.features.auth.domain.User;
import com.techpulse.techradar.features.auth.ports.UserRepository;
import com.techpulse.techradar.shared.exception.ConflictException;
import com.techpulse.techradar.shared.exception.ErrorCode;
import com.techpulse.techradar.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * User-lookup and validation helpers shared by {@link ProfileService} (self-service profile
 * operations) and {@link AdminUserService} (admin-only user CRUD), so neither of those two
 * unrelated responsibilities needs to depend on the other.
 */
@Component
@RequiredArgsConstructor
public class UserAccountValidator {

    private final UserRepository userRepository;

    public Mono<User> findByIdOrThrow(String userId) {
        return userRepository.findById(userId)
                .switchIfEmpty(Mono.error(new NotFoundException("User not found")));
    }

    public Mono<Void> validateEmailUnique(String email, String currentUserId) {
        return userRepository.findByEmail(email)
                .flatMap(found -> {
                    if (currentUserId == null || !found.getId().toString().equals(currentUserId)) {
                        return Mono.error(new ConflictException(ErrorCode.EMAIL_ALREADY_EXISTS, "Email already registered"));
                    }
                    return Mono.empty();
                })
                .then();
    }
}
