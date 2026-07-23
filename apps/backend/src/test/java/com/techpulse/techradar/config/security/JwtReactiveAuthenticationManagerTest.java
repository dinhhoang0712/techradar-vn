package com.techpulse.techradar.config.security;

import com.techpulse.techradar.config.JwtTokenProvider;
import com.techpulse.techradar.shared.redis.SecurityStampService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtReactiveAuthenticationManagerTest {

    @Mock
    private JwtTokenProvider jwtTokenProvider;
    @Mock
    private SecurityStampService securityStampService;

    private JwtReactiveAuthenticationManager manager;

    @BeforeEach
    void setUp() {
        manager = new JwtReactiveAuthenticationManager(jwtTokenProvider, securityStampService);
    }

    private Authentication unauthenticatedWith(String token) {
        return new UsernamePasswordAuthenticationToken(token, token);
    }

    private void stubValidToken(String token, String role, List<String> permissions, String stamp) {
        when(jwtTokenProvider.isTokenValid(token)).thenReturn(true);
        when(jwtTokenProvider.isAccessToken(token)).thenReturn(true);
        when(jwtTokenProvider.getUserIdFromToken(token)).thenReturn("user-1");
        when(jwtTokenProvider.getRoleFromToken(token)).thenReturn(role);
        when(jwtTokenProvider.getEmailFromToken(token)).thenReturn("dev@example.com");
        when(jwtTokenProvider.getPermissionsFromToken(token)).thenReturn(permissions);
        when(jwtTokenProvider.getStampFromToken(token)).thenReturn(stamp);
    }

    @Test
    void authenticate_buildsAuthenticationWithUserIdAsNameAndRolePlusPermissionAuthorities() {
        stubValidToken("valid-token", "admin", List.of("user:manage", "cms:manage"), "stamp-1");
        when(securityStampService.currentStamp("user-1")).thenReturn(Mono.empty());

        StepVerifier.create(manager.authenticate(unauthenticatedWith("valid-token")))
                .assertNext(auth -> {
                    assertThat(auth.getName()).isEqualTo("user-1");
                    assertThat(auth.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                            .containsExactlyInAnyOrder("ROLE_ADMIN", "user:manage", "cms:manage");
                    assertThat(auth.getDetails()).isEqualTo("dev@example.com");
                })
                .verifyComplete();
    }

    @Test
    void authenticate_defaultsToRoleUser_whenRoleClaimIsNull() {
        stubValidToken("valid-token", null, List.of(), "stamp-1");
        when(securityStampService.currentStamp("user-1")).thenReturn(Mono.empty());

        StepVerifier.create(manager.authenticate(unauthenticatedWith("valid-token")))
                .assertNext(auth -> assertThat(auth.getAuthorities())
                        .extracting(GrantedAuthority::getAuthority)
                        .containsExactly("ROLE_USER"))
                .verifyComplete();
    }

    @Test
    void authenticate_defaultsToRoleUser_whenRoleClaimIsBlank() {
        stubValidToken("valid-token", "  ", List.of(), "stamp-1");
        when(securityStampService.currentStamp("user-1")).thenReturn(Mono.empty());

        StepVerifier.create(manager.authenticate(unauthenticatedWith("valid-token")))
                .assertNext(auth -> assertThat(auth.getAuthorities())
                        .extracting(GrantedAuthority::getAuthority)
                        .containsExactly("ROLE_USER"))
                .verifyComplete();
    }

    @Test
    void authenticate_uppercasesRole() {
        stubValidToken("valid-token", "admin", List.of(), "stamp-1");
        when(securityStampService.currentStamp("user-1")).thenReturn(Mono.empty());

        StepVerifier.create(manager.authenticate(unauthenticatedWith("valid-token")))
                .assertNext(auth -> assertThat(auth.getAuthorities())
                        .extracting(GrantedAuthority::getAuthority)
                        .containsExactly("ROLE_ADMIN"))
                .verifyComplete();
    }

    @Test
    void authenticate_fails_whenTokenIsInvalid() {
        when(jwtTokenProvider.isTokenValid("bad-token")).thenReturn(false);

        StepVerifier.create(manager.authenticate(unauthenticatedWith("bad-token")))
                .expectError(BadCredentialsException.class)
                .verify();
    }

    @Test
    void authenticate_fails_whenTokenIsValidButNotAnAccessToken() {
        when(jwtTokenProvider.isTokenValid("refresh-token")).thenReturn(true);
        when(jwtTokenProvider.isAccessToken("refresh-token")).thenReturn(false);

        StepVerifier.create(manager.authenticate(unauthenticatedWith("refresh-token")))
                .expectError(BadCredentialsException.class)
                .verify();
    }

    @Test
    void authenticate_succeeds_whenNoStampHasEverBeenRecordedForTheUser() {
        // No admin action has ever bumped this user's stamp -> nothing in Redis yet. Trust the
        // token rather than requiring every existing user to be backfilled into Redis.
        stubValidToken("valid-token", "user", List.of(), "stamp-1");
        when(securityStampService.currentStamp("user-1")).thenReturn(Mono.empty());

        StepVerifier.create(manager.authenticate(unauthenticatedWith("valid-token")))
                .assertNext(auth -> assertThat(auth.getName()).isEqualTo("user-1"))
                .verifyComplete();
    }

    @Test
    void authenticate_succeeds_whenTokenStampMatchesCurrentStamp() {
        stubValidToken("valid-token", "user", List.of(), "stamp-1");
        when(securityStampService.currentStamp("user-1")).thenReturn(Mono.just("stamp-1"));

        StepVerifier.create(manager.authenticate(unauthenticatedWith("valid-token")))
                .assertNext(auth -> assertThat(auth.getName()).isEqualTo("user-1"))
                .verifyComplete();
    }

    @Test
    void authenticate_fails_whenTokenStampIsStale_becauseRoleOrStatusChangedSince() {
        stubValidToken("valid-token", "user", List.of(), "stale-stamp");
        when(securityStampService.currentStamp("user-1")).thenReturn(Mono.just("current-stamp"));

        StepVerifier.create(manager.authenticate(unauthenticatedWith("valid-token")))
                .expectError(BadCredentialsException.class)
                .verify();
    }
}
