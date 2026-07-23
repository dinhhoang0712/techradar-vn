package com.techpulse.techradar.features.auth.application;

import com.techpulse.techradar.config.JwtTokenProvider;
import com.techpulse.techradar.features.auth.domain.User;
import com.techpulse.techradar.features.auth.ports.RolePermissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Issues access/refresh tokens for an authenticated {@link User} and assembles the
 * {@link LoginResponse} returned after login and registration, so the token/response
 * wiring lives in exactly one place instead of being copy-pasted per use case.
 */
@Component
@RequiredArgsConstructor
public class TokenIssuer {

    private final JwtTokenProvider jwtTokenProvider;
    private final RolePermissionRepository rolePermissionRepository;

    @Value("${app.jwt.expiration}")
    private long jwtExpiration;

    public Mono<LoginResponse> issueFor(User user) {
        return rolePermissionRepository.findPermissionCodesByRole(user.getRole())
                .collectList()
                .map(permissions -> buildResponse(user, permissions));
    }

    private LoginResponse buildResponse(User user, List<String> permissions) {
        String accessToken = jwtTokenProvider.generateToken(
                user.getId().toString(),
                user.getEmail(),
                user.getRole(),
                permissions,
                user.getSecurityStamp().toString()
        );
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId().toString());

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .userId(user.getId().toString())
                .email(user.getEmail())
                .role(user.getRole())
                // Reuse the configured token lifetime instead of re-parsing/verifying the
                // access token we just signed above just to read its expiry back out.
                .expiresIn(jwtExpiration)
                .build();
    }
}
