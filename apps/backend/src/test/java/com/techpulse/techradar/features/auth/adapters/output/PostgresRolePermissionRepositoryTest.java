package com.techpulse.techradar.features.auth.adapters.output;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.r2dbc.core.RowsFetchSpec;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.function.Function;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostgresRolePermissionRepositoryTest {

    @Mock
    private DatabaseClient dbClient;
    @Mock
    private DatabaseClient.GenericExecuteSpec executeSpec;
    @Mock
    private RowsFetchSpec<String> stringRowsFetchSpec;
    @Mock
    private RowsFetchSpec<Boolean> booleanRowsFetchSpec;

    private PostgresRolePermissionRepository repository;

    @BeforeEach
    void setUp() {
        repository = new PostgresRolePermissionRepository(dbClient);
    }

    @Test
    @SuppressWarnings("unchecked")
    void findPermissionCodesByRole_bindsRoleCode_andReturnsEveryPermissionCode() {
        when(dbClient.sql(
                "SELECT p.code FROM permissions p " +
                        "JOIN role_permissions rp ON rp.permission_id = p.id " +
                        "JOIN roles r ON r.id = rp.role_id " +
                        "WHERE r.code = :roleCode"))
                .thenReturn(executeSpec);
        when(executeSpec.bind("roleCode", "admin")).thenReturn(executeSpec);
        when(executeSpec.map(any(Function.class))).thenReturn(stringRowsFetchSpec);
        when(stringRowsFetchSpec.all()).thenReturn(Flux.just("user:manage", "cms:manage"));

        StepVerifier.create(repository.findPermissionCodesByRole("admin"))
                .expectNext("user:manage", "cms:manage")
                .verifyComplete();
    }

    @Test
    void roleExists_returnsTrue_whenRoleRowFound() {
        when(dbClient.sql("SELECT 1 FROM roles WHERE code = :code")).thenReturn(executeSpec);
        when(executeSpec.bind("code", "admin")).thenReturn(executeSpec);
        when(executeSpec.map(any(Function.class))).thenReturn(booleanRowsFetchSpec);
        when(booleanRowsFetchSpec.one()).thenReturn(Mono.just(true));

        StepVerifier.create(repository.roleExists("admin"))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    void roleExists_returnsFalse_whenNoRoleRowFound() {
        when(dbClient.sql("SELECT 1 FROM roles WHERE code = :code")).thenReturn(executeSpec);
        when(executeSpec.bind("code", "superadmin")).thenReturn(executeSpec);
        when(executeSpec.map(any(Function.class))).thenReturn(booleanRowsFetchSpec);
        when(booleanRowsFetchSpec.one()).thenReturn(Mono.empty());

        StepVerifier.create(repository.roleExists("superadmin"))
                .expectNext(false)
                .verifyComplete();
    }
}
