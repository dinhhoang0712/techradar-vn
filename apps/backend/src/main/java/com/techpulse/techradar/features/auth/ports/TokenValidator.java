package com.techpulse.techradar.features.auth.ports;

/**
 * Output port for validating/reading previously-issued JWTs. Abstracts the auth use cases that
 * only ever need to check/inspect a token away from the concrete {@code JwtTokenProvider} —
 * mirrors how {@link UserRepository} abstracts persistence. Token *generation* is a separate
 * concern and stays behind {@code TokenIssuer} / the concrete provider.
 */
public interface TokenValidator {

    /** Whether the token is well-formed, signed correctly, and not expired. */
    boolean isValid(String token);

    /** Whether the token's {@code token_type} claim marks it as a refresh token. */
    boolean isRefreshToken(String token);

    /** The user id carried in the token's subject claim. */
    String extractUserId(String token);

    /** The token's expiration time, in epoch milliseconds. */
    long expirationTimeMillis(String token);
}
