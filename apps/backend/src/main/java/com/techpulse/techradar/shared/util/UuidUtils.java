package com.techpulse.techradar.shared.util;

import java.util.UUID;

/**
 * Helpers for validating UUID strings without throwing on malformed input.
 * <p>
 * Several features independently re-implemented this try/catch idiom
 * (e.g. {@code ResetPasswordUseCase.isUuid}, {@code ChatUseCase.isValidUuid}); this is the
 * single shared implementation they should all delegate to.
 */
public final class UuidUtils {

    private UuidUtils() {
    }

    /**
     * @return {@code true} when {@code value} is a syntactically valid UUID; {@code false} for
     * {@code null}, blank, or malformed input.
     */
    public static boolean isValid(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
