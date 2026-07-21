package com.techpulse.techradar.features.auth.adapters.output;

import com.techpulse.techradar.config.JwtTokenProvider;
import com.techpulse.techradar.features.auth.ports.TokenValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Adapts the concrete {@link JwtTokenProvider} to the {@link TokenValidator} port. */
@Component
@RequiredArgsConstructor
public class JwtTokenValidatorAdapter implements TokenValidator {

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    public boolean isValid(String token) {
        return jwtTokenProvider.isTokenValid(token);
    }

    @Override
    public boolean isRefreshToken(String token) {
        return jwtTokenProvider.isRefreshToken(token);
    }

    @Override
    public String extractUserId(String token) {
        return jwtTokenProvider.getUserIdFromToken(token);
    }

    @Override
    public long expirationTimeMillis(String token) {
        return jwtTokenProvider.getExpirationTime(token);
    }
}
