package com.techpulse.techradar.features.user.application;

import com.techpulse.techradar.features.auth.domain.User;
import com.techpulse.techradar.features.auth.ports.RolePermissionRepository;
import com.techpulse.techradar.features.auth.ports.UserRepository;
import com.techpulse.techradar.shared.exception.BadRequestException;
import com.techpulse.techradar.shared.exception.ConflictException;
import com.techpulse.techradar.shared.exception.ErrorCode;
import com.techpulse.techradar.shared.exception.NotFoundException;
import com.techpulse.techradar.shared.redis.SecurityStampService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private UserAccountValidator accountValidator;
    @Mock
    private SecurityStampService securityStampService;
    @Mock
    private RolePermissionRepository rolePermissionRepository;

    private AdminUserService service;

    @BeforeEach
    void setUp() {
        service = new AdminUserService(userRepository, passwordEncoder, accountValidator, securityStampService, rolePermissionRepository);
    }

    @Test
    void listUsers_delegatesToRepository() {
        User user = User.builder().id(UUID.randomUUID()).email("dev@example.com").build();
        when(userRepository.findAll()).thenReturn(Flux.just(user));

        StepVerifier.create(service.listUsers()).expectNext(user).verifyComplete();
    }

    @Test
    void createUser_appliesDefaultsAndEncodesPassword() {
        when(accountValidator.validateEmailUnique("dev@example.com", null)).thenReturn(Mono.empty());
        when(passwordEncoder.encode("plain")).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(service.createUser("dev@example.com", "plain", "Dev", null, null, null))
                .assertNext(saved -> {
                    assertThat(saved.getPasswordHash()).isEqualTo("hashed");
                    assertThat(saved.getRole()).isEqualTo("user");
                    assertThat(saved.getStatus()).isEqualTo("ACTIVE");
                    assertThat(saved.getSubscriptionTier()).isEqualTo("FREE");
                })
                .verifyComplete();
    }

    @Test
    void createUser_normalizesAdminRole() {
        when(accountValidator.validateEmailUnique(anyString(), eq(null))).thenReturn(Mono.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(rolePermissionRepository.roleExists("admin")).thenReturn(Mono.just(true));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(service.createUser("a@example.com", "plain", "A", "ADMIN", "SUSPENDED", "PRO"))
                .assertNext(saved -> {
                    assertThat(saved.getRole()).isEqualTo("admin");
                    assertThat(saved.getStatus()).isEqualTo("SUSPENDED");
                    assertThat(saved.getSubscriptionTier()).isEqualTo("PRO");
                })
                .verifyComplete();
    }

    @Test
    void createUser_rejectsUnknownRole_withoutSaving() {
        when(accountValidator.validateEmailUnique(anyString(), eq(null))).thenReturn(Mono.empty());
        when(rolePermissionRepository.roleExists("superadmin")).thenReturn(Mono.just(false));

        StepVerifier.create(service.createUser("a@example.com", "plain", "A", "superadmin", null, null))
                .expectError(BadRequestException.class)
                .verify();

        verify(userRepository, never()).save(any());
    }

    @Test
    void createUser_allowsAnyRoleThatExistsInTheRolesTable_provingRbacIsDataDriven() {
        // The RBAC design lets a new role (e.g. "moderator", added purely as data in
        // V25__moderator_role.sql) become assignable here with zero code change - this pins that
        // behavior instead of a hardcoded admin/user binary.
        when(accountValidator.validateEmailUnique(anyString(), eq(null))).thenReturn(Mono.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(rolePermissionRepository.roleExists("moderator")).thenReturn(Mono.just(true));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(service.createUser("mod@example.com", "plain", "Mod", "moderator", null, null))
                .assertNext(saved -> assertThat(saved.getRole()).isEqualTo("moderator"))
                .verifyComplete();
    }

    @Test
    void createUser_rejectsDuplicateEmail_withoutSaving() {
        ConflictException conflict = new ConflictException(ErrorCode.EMAIL_ALREADY_EXISTS, "Email already registered");
        when(accountValidator.validateEmailUnique("dup@example.com", null)).thenReturn(Mono.error(conflict));

        StepVerifier.create(service.createUser("dup@example.com", "plain", "Dup", null, null, null))
                .expectErrorMatches(e -> e == conflict)
                .verify();

        verify(userRepository, never()).save(any());
    }

    @Test
    void alterUser_updatesOnlyProvidedFields() {
        User existing = User.builder()
                .id(UUID.randomUUID())
                .email("old@example.com")
                .fullName("Old Name")
                .role("user")
                .status("ACTIVE")
                .subscriptionTier("FREE")
                .passwordHash("old-hash")
                .build();
        when(accountValidator.findByIdOrThrow(existing.getId().toString())).thenReturn(Mono.just(existing));
        when(userRepository.save(existing)).thenReturn(Mono.just(existing));

        StepVerifier.create(service.alterUser(existing.getId().toString(), null, null, "New Name", null, null, null))
                .assertNext(saved -> {
                    assertThat(saved.getFullName()).isEqualTo("New Name");
                    assertThat(saved.getEmail()).isEqualTo("old@example.com");
                    assertThat(saved.getPasswordHash()).isEqualTo("old-hash");
                })
                .verifyComplete();

        verify(accountValidator, never()).validateEmailUnique(any(), any());
    }

    @Test
    void alterUser_validatesEmailUniqueness_onlyWhenEmailActuallyChanges() {
        User existing = User.builder().id(UUID.randomUUID()).email("old@example.com").build();
        when(accountValidator.findByIdOrThrow(existing.getId().toString())).thenReturn(Mono.just(existing));
        when(accountValidator.validateEmailUnique("new@example.com", existing.getId().toString())).thenReturn(Mono.empty());
        when(userRepository.save(existing)).thenReturn(Mono.just(existing));

        StepVerifier.create(service.alterUser(existing.getId().toString(), "new@example.com", null, null, null, null, null))
                .assertNext(saved -> assertThat(saved.getEmail()).isEqualTo("new@example.com"))
                .verifyComplete();

        verify(accountValidator).validateEmailUnique("new@example.com", existing.getId().toString());
    }

    @Test
    void alterUser_skipsEmailValidation_whenEmailUnchangedCaseInsensitive() {
        User existing = User.builder().id(UUID.randomUUID()).email("same@example.com").build();
        when(accountValidator.findByIdOrThrow(existing.getId().toString())).thenReturn(Mono.just(existing));
        when(userRepository.save(existing)).thenReturn(Mono.just(existing));

        StepVerifier.create(service.alterUser(existing.getId().toString(), "SAME@example.com", null, null, null, null, null))
                .expectNextCount(1)
                .verifyComplete();

        verify(accountValidator, never()).validateEmailUnique(any(), any());
    }

    @Test
    void alterUser_propagatesNotFound_whenUserMissing() {
        String userId = UUID.randomUUID().toString();
        when(accountValidator.findByIdOrThrow(userId)).thenReturn(Mono.error(new NotFoundException("User not found")));

        StepVerifier.create(service.alterUser(userId, null, null, null, null, null, null))
                .expectError(NotFoundException.class)
                .verify();

        verify(userRepository, never()).save(any());
    }

    @Test
    void deleteUser_completes_whenRowDeleted() {
        String userId = UUID.randomUUID().toString();
        when(userRepository.deleteById(userId)).thenReturn(Mono.just(1L));

        StepVerifier.create(service.deleteUser(userId)).verifyComplete();
    }

    @Test
    void deleteUser_throwsNotFound_whenNoRowDeleted() {
        String userId = UUID.randomUUID().toString();
        when(userRepository.deleteById(userId)).thenReturn(Mono.just(0L));

        StepVerifier.create(service.deleteUser(userId)).expectError(NotFoundException.class).verify();
    }

    @Test
    void alterUser_bumpsSecurityStamp_whenRoleActuallyChanges() {
        User existing = User.builder()
                .id(UUID.randomUUID()).email("dev@example.com").role("user").status("ACTIVE")
                .build();
        when(accountValidator.findByIdOrThrow(existing.getId().toString())).thenReturn(Mono.just(existing));
        when(rolePermissionRepository.roleExists("admin")).thenReturn(Mono.just(true));
        when(userRepository.save(existing)).thenReturn(Mono.just(existing));
        when(securityStampService.set(eq(existing.getId().toString()), any(UUID.class))).thenReturn(Mono.empty());

        StepVerifier.create(service.alterUser(existing.getId().toString(), null, null, null, "admin", null, null))
                .assertNext(saved -> assertThat(saved.getRole()).isEqualTo("admin"))
                .verifyComplete();

        verify(securityStampService).set(eq(existing.getId().toString()), any(UUID.class));
    }

    @Test
    void alterUser_doesNotBumpSecurityStamp_whenRoleProvidedButUnchanged() {
        User existing = User.builder()
                .id(UUID.randomUUID()).email("dev@example.com").role("admin").status("ACTIVE")
                .build();
        when(accountValidator.findByIdOrThrow(existing.getId().toString())).thenReturn(Mono.just(existing));
        when(rolePermissionRepository.roleExists("admin")).thenReturn(Mono.just(true));
        when(userRepository.save(existing)).thenReturn(Mono.just(existing));

        StepVerifier.create(service.alterUser(existing.getId().toString(), null, null, null, "admin", null, null))
                .expectNextCount(1)
                .verifyComplete();

        verify(securityStampService, never()).set(any(), any());
    }

    @Test
    void alterUser_rejectsUnknownRole_withoutSaving() {
        User existing = User.builder()
                .id(UUID.randomUUID()).email("dev@example.com").role("user").status("ACTIVE")
                .build();
        when(accountValidator.findByIdOrThrow(existing.getId().toString())).thenReturn(Mono.just(existing));
        when(rolePermissionRepository.roleExists("superadmin")).thenReturn(Mono.just(false));

        StepVerifier.create(service.alterUser(existing.getId().toString(), null, null, null, "superadmin", null, null))
                .expectError(BadRequestException.class)
                .verify();

        verify(userRepository, never()).save(any());
    }

    @Test
    void alterUser_bumpsSecurityStamp_whenStatusChanges() {
        User existing = User.builder()
                .id(UUID.randomUUID()).email("dev@example.com").role("user").status("ACTIVE")
                .build();
        when(accountValidator.findByIdOrThrow(existing.getId().toString())).thenReturn(Mono.just(existing));
        when(userRepository.save(existing)).thenReturn(Mono.just(existing));
        when(securityStampService.set(eq(existing.getId().toString()), any(UUID.class))).thenReturn(Mono.empty());

        StepVerifier.create(service.alterUser(existing.getId().toString(), null, null, null, null, "SUSPENDED", null))
                .assertNext(saved -> assertThat(saved.getStatus()).isEqualTo("SUSPENDED"))
                .verifyComplete();

        verify(securityStampService).set(eq(existing.getId().toString()), any(UUID.class));
    }

    @Test
    void alterUser_bumpsSecurityStamp_whenPasswordChanges() {
        User existing = User.builder()
                .id(UUID.randomUUID()).email("dev@example.com").role("user").status("ACTIVE")
                .passwordHash("old-hash")
                .build();
        when(accountValidator.findByIdOrThrow(existing.getId().toString())).thenReturn(Mono.just(existing));
        when(passwordEncoder.encode("newpassword")).thenReturn("new-hash");
        when(userRepository.save(existing)).thenReturn(Mono.just(existing));
        when(securityStampService.set(eq(existing.getId().toString()), any(UUID.class))).thenReturn(Mono.empty());

        StepVerifier.create(service.alterUser(existing.getId().toString(), null, "newpassword", null, null, null, null))
                .assertNext(saved -> assertThat(saved.getPasswordHash()).isEqualTo("new-hash"))
                .verifyComplete();

        verify(securityStampService).set(eq(existing.getId().toString()), any(UUID.class));
    }
}
