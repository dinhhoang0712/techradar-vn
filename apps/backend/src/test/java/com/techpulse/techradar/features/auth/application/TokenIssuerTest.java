package com.techpulse.techradar.features.auth.application;

import com.techpulse.techradar.config.JwtTokenProvider;
import com.techpulse.techradar.features.auth.domain.User;
import com.techpulse.techradar.features.auth.ports.RolePermissionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TokenIssuerTest {

    private static final long JWT_EXPIRATION = 3_600_000L;

    @Mock
    private JwtTokenProvider jwtTokenProvider;
    @Mock
    private RolePermissionRepository rolePermissionRepository;

    private TokenIssuer tokenIssuer;

    @BeforeEach
    void setUp() {
        tokenIssuer = new TokenIssuer(jwtTokenProvider, rolePermissionRepository);
        ReflectionTestUtils.setField(tokenIssuer, "jwtExpiration", JWT_EXPIRATION);
    }

    @Test
    void issueFor_assemblesLoginResponse_fromUserAndGeneratedTokens() {
        User user = User.builder()
                .id(UUID.randomUUID()).email("dev@example.com").role("admin")
                .securityStamp(UUID.randomUUID())
                .build();
        when(rolePermissionRepository.findPermissionCodesByRole("admin"))
                .thenReturn(Flux.just("user:manage", "cms:manage"));
        when(jwtTokenProvider.generateToken(
                user.getId().toString(), user.getEmail(), user.getRole(),
                List.of("user:manage", "cms:manage"), user.getSecurityStamp().toString()))
                .thenReturn("access-token");
        when(jwtTokenProvider.generateRefreshToken(user.getId().toString())).thenReturn("refresh-token");

        StepVerifier.create(tokenIssuer.issueFor(user))
                .assertNext(response -> {
                    assertThat(response.getAccessToken()).isEqualTo("access-token");
                    assertThat(response.getRefreshToken()).isEqualTo("refresh-token");
                    assertThat(response.getUserId()).isEqualTo(user.getId().toString());
                    assertThat(response.getEmail()).isEqualTo(user.getEmail());
                    assertThat(response.getRole()).isEqualTo("admin");
                    assertThat(response.getExpiresIn()).isEqualTo(JWT_EXPIRATION);
                })
                .verifyComplete();
    }

    @Test
    void issueFor_passesEmptyPermissionList_whenRoleHasNoPermissions() {
        User user = User.builder()
                .id(UUID.randomUUID()).email("dev@example.com").role("user")
                .securityStamp(UUID.randomUUID())
                .build();
        when(rolePermissionRepository.findPermissionCodesByRole("user")).thenReturn(Flux.empty());
        when(jwtTokenProvider.generateToken(
                user.getId().toString(), user.getEmail(), user.getRole(),
                List.of(), user.getSecurityStamp().toString()))
                .thenReturn("access-token");
        when(jwtTokenProvider.generateRefreshToken(user.getId().toString())).thenReturn("refresh-token");

        StepVerifier.create(tokenIssuer.issueFor(user))
                .assertNext(response -> assertThat(response.getAccessToken()).isEqualTo("access-token"))
                .verifyComplete();
    }
}
