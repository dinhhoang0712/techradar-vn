package com.techpulse.techradar.features.auth.ports;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Output port for the role -> permission-code lookup used to populate the JWT {@code permissions}
 * claim at login/register/refresh time.
 */
public interface RolePermissionRepository {

    Flux<String> findPermissionCodesByRole(String roleCode);

    /** @return true if {@code code} names a row in the {@code roles} table. */
    Mono<Boolean> roleExists(String code);
}
