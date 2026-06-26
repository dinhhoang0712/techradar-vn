package com.techpulse.techradar.config;

import com.techpulse.techradar.config.security.JwtReactiveAuthenticationManager;
import com.techpulse.techradar.config.security.JwtServerAuthenticationConverter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.AuthenticationWebFilter;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.security.web.server.util.matcher.NegatedServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers;
import org.springframework.web.cors.reactive.CorsConfigurationSource;

/**
 * Spring Security configuration for the reactive stack.
 * <p>
 * Stateless JWT authentication: a Bearer token is validated by a custom
 * {@link AuthenticationWebFilter} that populates the reactive security context with the
 * user id (as principal name) and a {@code ROLE_<ROLE>} authority.
 */
@Configuration
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    /** Endpoints reachable without a valid JWT. */
    // NOTE: spring.webflux.base-path (/api/v1) is stripped by the HttpHandler BEFORE the security
    // WebFilter chain runs, so these matchers must NOT include the /api/v1 prefix.
    private static final String[] PUBLIC_PATHS = {
            "/auth/login",
            "/auth/register",
            "/auth/refresh",
            "/auth/logout",
            "/auth/forgot-password",
            "/auth/reset-password",
            "/health",
            "/status",
            "/actuator/**",
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/v3/api-docs/**",
            "/webjars/**"
    };

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(
            ServerHttpSecurity http,
            JwtReactiveAuthenticationManager authenticationManager,
            JwtServerAuthenticationConverter authenticationConverter,
            CorsConfigurationSource corsConfigurationSource) {

        AuthenticationWebFilter jwtFilter = new AuthenticationWebFilter(authenticationManager);
        jwtFilter.setServerAuthenticationConverter(authenticationConverter);
        jwtFilter.setSecurityContextRepository(NoOpServerSecurityContextRepository.getInstance());
        // Only attempt JWT authentication on protected paths so a stale token never blocks a public endpoint.
        ServerWebExchangeMatcher publicMatcher = ServerWebExchangeMatchers.matchers(
                ServerWebExchangeMatchers.pathMatchers(HttpMethod.OPTIONS, "/**"),
                ServerWebExchangeMatchers.pathMatchers(HttpMethod.GET, "/user/avatar/**"),
                ServerWebExchangeMatchers.pathMatchers(PUBLIC_PATHS));
        jwtFilter.setRequiresAuthenticationMatcher(new NegatedServerWebExchangeMatcher(publicMatcher));

        http
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                .authorizeExchange(authorize -> authorize
                        .pathMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .pathMatchers(HttpMethod.GET, "/user/avatar/**").permitAll()  // public avatar images
                        .pathMatchers(PUBLIC_PATHS).permitAll()
                        .anyExchange().authenticated())
                .addFilterAt(jwtFilter, SecurityWebFiltersOrder.AUTHENTICATION)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource));

        return http.build();
    }
}
