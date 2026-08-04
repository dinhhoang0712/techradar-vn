package com.techpulse.techradar.features.auth.domain;

/**
 * Fixed vocabulary for {@link User#getStatus()} — single source of truth backing {@code @OneOf}
 * on every request DTO that accepts a status (instead of repeating the literal list at each one).
 * See docs/adr/0010-oneof-validation-for-fixed-vocabulary-strings.md.
 */
public enum UserStatus {
    ACTIVE,
    INACTIVE,
    SUSPENDED
}
