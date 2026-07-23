package com.techpulse.techradar.config;

import io.jsonwebtoken.ExpiredJwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenProviderTest {

    private static final String SECRET = "test-secret-key-for-jwt-signing-must-be-long-enough-for-hs256-0123456789";

    private JwtTokenProvider provider;

    @BeforeEach
    void setUp() {
        provider = new JwtTokenProvider();
        ReflectionTestUtils.setField(provider, "jwtSecret", SECRET);
        ReflectionTestUtils.setField(provider, "jwtExpiration", 3_600_000L);
        ReflectionTestUtils.setField(provider, "refreshExpiration", 604_800_000L);
    }

    @Test
    void generateToken_roundTripsUserIdEmailRoleAndAccessTokenType() {
        String stamp = UUID.randomUUID().toString();
        String token = provider.generateToken("user-1", "dev@example.com", "admin", List.of("user:manage"), stamp);

        assertThat(provider.getUserIdFromToken(token)).isEqualTo("user-1");
        assertThat(provider.getEmailFromToken(token)).isEqualTo("dev@example.com");
        assertThat(provider.getRoleFromToken(token)).isEqualTo("admin");
        assertThat(provider.getPermissionsFromToken(token)).containsExactly("user:manage");
        assertThat(provider.getStampFromToken(token)).isEqualTo(stamp);
        assertThat(provider.getTokenTypeFromToken(token)).isEqualTo("access");
        assertThat(provider.isAccessToken(token)).isTrue();
        assertThat(provider.isRefreshToken(token)).isFalse();
        assertThat(provider.isTokenValid(token)).isTrue();
    }

    @Test
    void getPermissionsFromToken_returnsEmptyList_forRefreshToken_whichCarriesNoPermissionsClaim() {
        String token = provider.generateRefreshToken("user-1");

        assertThat(provider.getPermissionsFromToken(token)).isEmpty();
        assertThat(provider.getStampFromToken(token)).isNull();
    }

    @Test
    void generateRefreshToken_roundTripsUserIdAndRefreshTokenType() {
        String token = provider.generateRefreshToken("user-1");

        assertThat(provider.getUserIdFromToken(token)).isEqualTo("user-1");
        assertThat(provider.getTokenTypeFromToken(token)).isEqualTo("refresh");
        assertThat(provider.isRefreshToken(token)).isTrue();
        assertThat(provider.isAccessToken(token)).isFalse();
        assertThat(provider.getEmailFromToken(token)).isNull();
        assertThat(provider.getRoleFromToken(token)).isNull();
    }

    @Test
    void isTokenValid_returnsFalse_forGarbageString() {
        assertThat(provider.isTokenValid("not-a-real-jwt")).isFalse();
    }

    @Test
    void isAccessToken_returnsFalse_forGarbageString_withoutThrowing() {
        assertThat(provider.isAccessToken("not-a-real-jwt")).isFalse();
    }

    @Test
    void isRefreshToken_returnsFalse_forGarbageString_withoutThrowing() {
        assertThat(provider.isRefreshToken("not-a-real-jwt")).isFalse();
    }

    @Test
    void isTokenValid_returnsFalse_forTokenSignedWithADifferentSecret() {
        JwtTokenProvider otherProvider = new JwtTokenProvider();
        ReflectionTestUtils.setField(otherProvider, "jwtSecret", "a-completely-different-secret-key-also-long-enough-for-hs256-abcdef");
        ReflectionTestUtils.setField(otherProvider, "jwtExpiration", 3_600_000L);
        String token = otherProvider.generateToken("user-1", "dev@example.com", "user", List.of(), UUID.randomUUID().toString());

        assertThat(provider.isTokenValid(token)).isFalse();
    }

    @Test
    void getExpirationTime_returnsATimestampInTheFuture_forAFreshToken() {
        long before = System.currentTimeMillis();
        String token = provider.generateToken("user-1", "dev@example.com", "user", List.of(), UUID.randomUUID().toString());

        assertThat(provider.getExpirationTime(token)).isGreaterThan(before);
    }

    @Test
    void isTokenExpired_returnsFalse_forAFreshToken() {
        String token = provider.generateToken("user-1", "dev@example.com", "user", List.of(), UUID.randomUUID().toString());

        assertThat(provider.isTokenExpired(token)).isFalse();
    }

    @Test
    void parsingAnAlreadyExpiredToken_throwsExpiredJwtException_ratherThanReturningATimestamp() {
        // jjwt validates the `exp` claim inside parseSignedClaims() itself, so an expired token
        // never reaches the isTokenExpired()/getExpirationTime() comparison logic — it fails at
        // parse time instead. This is why every OTHER validation method here (isTokenValid,
        // isAccessToken, isRefreshToken) wraps parsing in try/catch and isTokenExpired/
        // getExpirationTime do NOT — the only real caller of getExpirationTime
        // (JwtTokenValidatorAdapter -> LogoutUseCase.remainingTtl) has its own try/catch for
        // exactly this reason.
        ReflectionTestUtils.setField(provider, "jwtExpiration", -1000L);
        String expiredToken = provider.generateToken("user-1", "dev@example.com", "user", List.of(), UUID.randomUUID().toString());

        assertThatThrownBy(() -> provider.getExpirationTime(expiredToken))
                .isInstanceOf(ExpiredJwtException.class);
        assertThatThrownBy(() -> provider.isTokenExpired(expiredToken))
                .isInstanceOf(ExpiredJwtException.class);
        assertThat(provider.isTokenValid(expiredToken)).isFalse();
    }
}
