package com.techpulse.techradar.config.security;

import com.techpulse.techradar.config.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;

/**
 * Validates the raw JWT produced by {@link JwtServerAuthenticationConverter} and, on success,
 * builds an authenticated principal whose name is the user id and whose authority is
 * {@code ROLE_<ROLE>} so method security ({@code @PreAuthorize("hasRole('ADMIN')")}) works.
 */
@Component
@RequiredArgsConstructor
public class JwtReactiveAuthenticationManager implements ReactiveAuthenticationManager {

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    public Mono<Authentication> authenticate(Authentication authentication) {
        String token = String.valueOf(authentication.getCredentials());
        return Mono.fromCallable(() -> buildAuthentication(token))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private Authentication buildAuthentication(String token) {
        if (!jwtTokenProvider.isTokenValid(token) || !jwtTokenProvider.isAccessToken(token)) {
            throw new BadCredentialsException("Invalid or expired access token");
        }
        String userId = jwtTokenProvider.getUserIdFromToken(token);
        String role = jwtTokenProvider.getRoleFromToken(token);
        String email = jwtTokenProvider.getEmailFromToken(token);

        String authority = "ROLE_" + (role == null || role.isBlank() ? "USER" : role.toUpperCase());
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                userId, null, List.of(new SimpleGrantedAuthority(authority)));
        auth.setDetails(email);
        return auth;
    }
}
