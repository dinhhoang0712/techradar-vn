package com.techpulse.techradar.shared.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import reactor.core.publisher.Mono;

import java.util.Objects;

/**
 * Helpers for reading the authenticated principal from the reactive security context.
 * <p>
 * The JWT authentication filter stores the user id as the {@link Authentication#getName() name}
 * of the principal, so {@link #currentUserId()} resolves to the user id (UUID string).
 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    /**
     * @return the authenticated user id, or an empty {@link Mono} when the request is anonymous.
     */
    public static Mono<String> currentUserId() {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .filter(Objects::nonNull)
                .filter(Authentication::isAuthenticated)
                .map(Authentication::getName);
    }
}
