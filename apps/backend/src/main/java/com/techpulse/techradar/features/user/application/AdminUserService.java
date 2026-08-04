package com.techpulse.techradar.features.user.application;

import com.techpulse.techradar.features.auth.domain.User;
import com.techpulse.techradar.features.auth.ports.RolePermissionRepository;
import com.techpulse.techradar.features.auth.ports.UserRepository;
import com.techpulse.techradar.shared.exception.BadRequestException;
import com.techpulse.techradar.shared.exception.ErrorCode;
import com.techpulse.techradar.shared.exception.NotFoundException;
import com.techpulse.techradar.shared.redis.SecurityStampService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Application service for admin-only user management: CRUD on arbitrary users, independent of
 * who is currently authenticated.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class AdminUserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserAccountValidator accountValidator;
    private final SecurityStampService securityStampService;
    private final RolePermissionRepository rolePermissionRepository;

    public Flux<User> listUsers() {
        return userRepository.findAll();
    }

    public Mono<User> createUser(String email,
                                 String rawPassword,
                                 String fullName,
                                 String role,
                                 String status,
                                 String subscriptionTier) {
        return accountValidator.validateEmailUnique(email, null)
                .then(normalizeRole(role))
                .flatMap(normalizedRole -> {
                    User user = User.builder()
                            .email(email)
                            .fullName(fullName)
                            .passwordHash(passwordEncoder.encode(rawPassword))
                            .role(normalizedRole)
                            .status(StringUtils.hasText(status) ? status : "ACTIVE")
                            .subscriptionTier(StringUtils.hasText(subscriptionTier) ? subscriptionTier : "FREE")
                            .build();
                    return userRepository.save(user);
                })
                .doOnSuccess(u -> log.info("Admin created user: id={}, email={}, role={}",
                        u.getId(), u.getEmail(), u.getRole()));
    }

    public Mono<User> alterUser(String userId,
                                String email,
                                String rawPassword,
                                String fullName,
                                String role,
                                String status,
                                String subscriptionTier) {
        return accountValidator.findByIdOrThrow(userId)
                .flatMap(existing -> {
                    if (StringUtils.hasText(email) && !email.equalsIgnoreCase(existing.getEmail())) {
                        return accountValidator.validateEmailUnique(email, existing.getId().toString())
                                .then(Mono.just(existing));
                    }
                    return Mono.just(existing);
                })
                .flatMap(existing -> {
                    // Snapshotted before ANY mutation (including role, resolved async below) - so
                    // whichever of changeRole/changePassword/changeStatus ends up rotating the
                    // stamp internally (or not, if unchanged) is what decides whether to propagate
                    // the new stamp, with no separate boolean bookkeeping to keep in sync.
                    UUID originalStamp = existing.getSecurityStamp();
                    return applyRoleChange(existing, role)
                            .map(u -> {
                                if (StringUtils.hasText(email)) {
                                    u.setEmail(email);
                                }
                                if (StringUtils.hasText(fullName)) {
                                    u.setFullName(fullName);
                                }
                                if (StringUtils.hasText(rawPassword)) {
                                    u.changePassword(passwordEncoder.encode(rawPassword));
                                }
                                if (StringUtils.hasText(status)) {
                                    u.changeStatus(status);
                                }
                                if (StringUtils.hasText(subscriptionTier)) {
                                    u.setSubscriptionTier(subscriptionTier);
                                }
                                return u;
                            })
                            .flatMap(u -> {
                                boolean bumpStamp = !Objects.equals(originalStamp, u.getSecurityStamp());
                                return userRepository.save(u)
                                        .flatMap(saved -> bumpStamp
                                                ? securityStampService.set(saved.getId().toString(), saved.getSecurityStamp()).thenReturn(saved)
                                                : Mono.just(saved));
                            });
                })
                .doOnSuccess(u -> log.info("Admin updated user: id={}", u.getId()));
    }

    private Mono<User> applyRoleChange(User existing, String role) {
        if (!StringUtils.hasText(role)) {
            return Mono.just(existing);
        }
        return normalizeRole(role).map(normalized -> {
            existing.changeRole(normalized);
            return existing;
        });
    }

    public Mono<Void> deleteUser(String userId) {
        return userRepository.deleteById(userId)
                .flatMap(rowsUpdated -> rowsUpdated == 0
                        ? Mono.<Void>error(new NotFoundException("User not found"))
                        : Mono.<Void>empty())
                .doOnSuccess(v -> log.info("Admin deleted user: id={}", userId));
    }

    /**
     * Validates against the {@code roles} table (V24/V25 migrations) instead of a hardcoded
     * admin/user binary, so a new role added purely as data (e.g. "moderator") becomes assignable
     * here immediately - no code change needed.
     */
    private Mono<String> normalizeRole(String role) {
        if (!StringUtils.hasText(role)) {
            return Mono.just("user");
        }
        String normalized = role.trim().toLowerCase(Locale.ROOT);
        return rolePermissionRepository.roleExists(normalized)
                .flatMap(exists -> exists
                        ? Mono.just(normalized)
                        : Mono.error(new BadRequestException(ErrorCode.VALIDATION_ERROR, "Unknown role: " + role)));
    }
}
