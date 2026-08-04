package com.techpulse.techradar.shared.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Restricts a String field to a fixed vocabulary — the Bean Validation equivalent of a DB
 * {@code CHECK(... IN (...))} constraint, for request fields that don't have (or shouldn't rely
 * solely on) one. The allowed values are an enum's constant names (e.g. {@code UserStatus}), not
 * a literal array repeated at every field sharing the same vocabulary — {@code CreateUserRequest}
 * and {@code UpdateUserRequest} both reference the same {@code UserStatus.class} instead of each
 * spelling out {@code ACTIVE}/{@code INACTIVE}/{@code SUSPENDED} by hand. Case-sensitive (exact
 * {@code Enum#name()} match) so a pass here guarantees the matching DB CHECK (if any) also passes
 * — see {@code docs/adr/0010-oneof-validation-for-fixed-vocabulary-strings.md}.
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = OneOfValidator.class)
public @interface OneOf {

    /** Enum whose constant names are the allowed values. */
    Class<? extends Enum<?>> value();

    String message() default "must be one of the allowed values";

    /** {@code true} (default): null/blank passes — combine with {@code @NotBlank} to also require presence. */
    boolean allowBlank() default true;

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
