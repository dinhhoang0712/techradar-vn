package com.techpulse.techradar.features.auth.domain;

/**
 * Fixed vocabulary for {@link User#getSubscriptionTier()} — single source of truth backing
 * {@code @OneOf} on every request DTO that accepts a tier (instead of repeating the literal list
 * at each one). See docs/adr/0010-oneof-validation-for-fixed-vocabulary-strings.md.
 */
public enum SubscriptionTier {
    FREE,
    PRO,
    ENTERPRISE
}
