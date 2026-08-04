package com.techpulse.techradar.features.user.adapters.input;

import com.techpulse.techradar.features.auth.domain.User;
import com.techpulse.techradar.features.system.application.AuditLogService;
import com.techpulse.techradar.features.user.application.AdminUserService;
import com.techpulse.techradar.shared.dto.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserAdminControllerTest {

    @Mock
    private AdminUserService userService;

    @Mock
    private AuditLogService auditLogService;

    private UserAdminController controller;

    @BeforeEach
    void setUp() {
        controller = new UserAdminController(userService, auditLogService);
        lenient().when(auditLogService.record(any(), any(), any(), any())).thenReturn(Mono.empty());
    }

    private static User user(UUID id) {
        return User.builder()
                .id(id)
                .email("admin-created@example.com")
                .fullName("Created User")
                .role("user")
                .status("ACTIVE")
                .subscriptionTier("FREE")
                .build();
    }

    @Test
    void listUsers_returnsAllUsersFromService() {
        User u = user(UUID.randomUUID());
        when(userService.listUsers()).thenReturn(Flux.just(u));

        StepVerifier.create(controller.listUsers())
                .assertNext(response -> assertThat(response.getBody().getData()).hasSize(1))
                .verifyComplete();
    }

    @Test
    void insertUser_recordsAuditLog_onSuccess() {
        UUID newId = UUID.randomUUID();
        User created = user(newId);
        CreateUserRequest request = CreateUserRequest.builder()
                .email("admin-created@example.com")
                .password("password123")
                .fullName("Created User")
                .role("user")
                .status("ACTIVE")
                .subscriptionTier("FREE")
                .build();

        when(userService.createUser("admin-created@example.com", "password123", "Created User", "user", "ACTIVE", "FREE"))
                .thenReturn(Mono.just(created));

        StepVerifier.create(controller.insertUser(request))
                .assertNext(response -> {
                    assertThat(response.getStatusCode().value()).isEqualTo(201);
                    ApiResponse<UserProfileResponse> body = response.getBody();
                    assertThat(body).isNotNull();
                    assertThat(body.getData().getId()).isEqualTo(newId.toString());
                })
                .verifyComplete();

        verify(auditLogService).record(eq("USER_CREATE"), eq("user"), eq(newId.toString()), any());
    }

    @Test
    void alterUser_recordsAuditLog_onSuccess() {
        UUID id = UUID.randomUUID();
        User updated = user(id);
        UpdateUserRequest request = UpdateUserRequest.builder()
                .email("admin-created@example.com")
                .fullName("Created User")
                .role("user")
                .status("ACTIVE")
                .subscriptionTier("FREE")
                .build();

        when(userService.alterUser(id.toString(), "admin-created@example.com", null, "Created User", "user", "ACTIVE", "FREE"))
                .thenReturn(Mono.just(updated));

        StepVerifier.create(controller.alterUser(id.toString(), request))
                .assertNext(response -> {
                    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
                    assertThat(response.getBody().getData().getId()).isEqualTo(id.toString());
                })
                .verifyComplete();

        verify(auditLogService).record(eq("USER_UPDATE"), eq("user"), eq(id.toString()), any());
    }

    @Test
    void deleteUser_recordsAuditLog_onSuccess() {
        UUID id = UUID.randomUUID();
        when(userService.deleteUser(id.toString())).thenReturn(Mono.empty());

        StepVerifier.create(controller.deleteUser(id.toString()))
                .assertNext(response -> {
                    assertThat(response.getStatusCode().value()).isEqualTo(204);
                    assertThat(response.getBody().isSuccess()).isTrue();
                })
                .verifyComplete();

        verify(userService).deleteUser(id.toString());
        verify(auditLogService).record(eq("USER_DELETE"), eq("user"), eq(id.toString()), any());
    }

    @Test
    void deleteUser_propagatesError_whenServiceFails() {
        // Note: this does NOT assert auditLogService.record(...) is never invoked as a Java method
        // call — `.then(auditLogService.record(...))` builds that Mono eagerly regardless of the
        // upstream outcome, so Mockito would see the call either way. What actually matters is that
        // Reactor's .then() never SUBSCRIBES to it when the upstream errors (no DB row gets written),
        // and that the failure still propagates to the caller instead of being swallowed.
        UUID id = UUID.randomUUID();
        when(userService.deleteUser(id.toString())).thenReturn(Mono.error(new RuntimeException("db down")));

        StepVerifier.create(controller.deleteUser(id.toString()))
                .expectError(RuntimeException.class)
                .verify();
    }
}
