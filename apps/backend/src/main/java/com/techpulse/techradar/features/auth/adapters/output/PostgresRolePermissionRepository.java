package com.techpulse.techradar.features.auth.adapters.output;

import com.techpulse.techradar.features.auth.ports.RolePermissionRepository;
import io.r2dbc.spi.Row;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * PostgreSQL adapter for the role -> permission-code lookup (roles/permissions/role_permissions
 * tables from V24__rbac_permissions.sql).
 */
@Repository
@RequiredArgsConstructor
public class PostgresRolePermissionRepository implements RolePermissionRepository {

    private final DatabaseClient dbClient;

    @Override
    public Flux<String> findPermissionCodesByRole(String roleCode) {
        return dbClient.sql(
                "SELECT p.code FROM permissions p " +
                "JOIN role_permissions rp ON rp.permission_id = p.id " +
                "JOIN roles r ON r.id = rp.role_id " +
                "WHERE r.code = :roleCode"
        )
                .bind("roleCode", roleCode)
                .map(row -> ((Row) row).get("code", String.class))
                .all();
    }

    @Override
    public Mono<Boolean> roleExists(String code) {
        return dbClient.sql("SELECT 1 FROM roles WHERE code = :code")
                .bind("code", code)
                .map(row -> true)
                .one()
                .defaultIfEmpty(false);
    }
}
