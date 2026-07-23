package com.techpulse.techradar.config.security;

import com.techpulse.techradar.config.JwtTokenProvider;
import com.techpulse.techradar.shared.redis.SecurityStampService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates the raw JWT produced by {@link JwtServerAuthenticationConverter} and, on success,
 * builds an authenticated principal whose name is the user id and whose authorities are
 * {@code ROLE_<ROLE>} plus one authority per RBAC permission code carried in the token, so
 * method security ({@code @PreAuthorize("hasAuthority('user:manage')")}) works.
 */
@Component
@RequiredArgsConstructor
public class JwtReactiveAuthenticationManager implements ReactiveAuthenticationManager {

    private final JwtTokenProvider jwtTokenProvider;
    private final SecurityStampService securityStampService;

    @Override
    public Mono<Authentication> authenticate(Authentication authentication) {
        String token = String.valueOf(authentication.getCredentials());
        return Mono.fromCallable(() -> decode(token))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(this::verifyStampAndBuildAuthentication);
    }

    private DecodedToken decode(String token) {
        if (!jwtTokenProvider.isTokenValid(token) || !jwtTokenProvider.isAccessToken(token)) {
            throw new BadCredentialsException("Invalid or expired access token");
        }
        return new DecodedToken(
                jwtTokenProvider.getUserIdFromToken(token),
                jwtTokenProvider.getRoleFromToken(token),
                jwtTokenProvider.getEmailFromToken(token),
                jwtTokenProvider.getPermissionsFromToken(token),
                jwtTokenProvider.getStampFromToken(token));
    }

    /**
     * Rejects the token if the user's security stamp has since been bumped (role/status/password
     * change) and no longer matches the one baked into this token at issuance time. A user who has
     * never had their stamp bumped has no Redis entry yet - that case trusts the token rather than
     * requiring a backfill of every existing user into Redis.
     */
    private Mono<Authentication> verifyStampAndBuildAuthentication(DecodedToken decoded) {
        return securityStampService.currentStamp(decoded.userId())
                .flatMap(currentStamp -> currentStamp.equals(decoded.stamp())
                        ? Mono.just(buildAuthentication(decoded))
                        : Mono.error(new BadCredentialsException("Token revoked: security stamp mismatch")))
                .switchIfEmpty(Mono.fromCallable(() -> buildAuthentication(decoded)));
    }

    private Authentication buildAuthentication(DecodedToken decoded) {
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        String role = decoded.role() == null || decoded.role().isBlank() ? "USER" : decoded.role().toUpperCase();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
        decoded.permissions().forEach(permission -> authorities.add(new SimpleGrantedAuthority(permission)));

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                decoded.userId(), null, authorities);
        auth.setDetails(decoded.email());
        return auth;
    }

    private record DecodedToken(String userId, String role, String email, List<String> permissions, String stamp) {
    }
}
