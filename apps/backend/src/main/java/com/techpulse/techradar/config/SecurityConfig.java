package com.techpulse.techradar.config;

import com.techpulse.techradar.config.security.JwtReactiveAuthenticationManager;
import com.techpulse.techradar.config.security.JwtServerAuthenticationConverter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.AuthenticationWebFilter;
import org.springframework.security.web.server.authentication.HttpStatusServerEntryPoint;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.security.web.server.util.matcher.NegatedServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers;
import org.springframework.web.cors.reactive.CorsConfigurationSource;

import java.util.List;

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

    /**
     * A single {@code (method, pattern)} public route, reachable without a valid JWT.
     * This is the ONE source of truth for "public" routes: both the JWT filter's
     * "skip authentication" matcher and the {@code authorizeExchange} {@code permitAll()}
     * matcher are derived from {@link #PUBLIC_ROUTES} so the two can never drift apart.
     */
    private record PublicRoute(HttpMethod method, String pattern) {

        /** A route open to every HTTP method. */
        static PublicRoute anyMethod(String pattern) {
            return new PublicRoute(null, pattern);
        }

        ServerWebExchangeMatcher toMatcher() {
            return method == null
                    ? ServerWebExchangeMatchers.pathMatchers(pattern)
                    : ServerWebExchangeMatchers.pathMatchers(method, pattern);
        }
    }

    /** Endpoints reachable without a valid JWT. */
    // NOTE: spring.webflux.base-path (/api/v1) is stripped by the HttpHandler BEFORE the security
    // WebFilter chain runs, so these matchers must NOT include the /api/v1 prefix.
    private static final List<PublicRoute> PUBLIC_ROUTES = List.of(
            PublicRoute.anyMethod("/auth/login"),
            PublicRoute.anyMethod("/auth/register"),
            PublicRoute.anyMethod("/auth/refresh"),
            PublicRoute.anyMethod("/auth/logout"),
            PublicRoute.anyMethod("/auth/forgot-password"),
            PublicRoute.anyMethod("/auth/reset-password"),
            PublicRoute.anyMethod("/health"),
            PublicRoute.anyMethod("/status"),
            PublicRoute.anyMethod("/stats/public"),
            PublicRoute.anyMethod("/actuator/**"),
            PublicRoute.anyMethod("/swagger-ui.html"),
            PublicRoute.anyMethod("/swagger-ui/**"),
            PublicRoute.anyMethod("/v3/api-docs/**"),
            PublicRoute.anyMethod("/webjars/**"),
            PublicRoute.anyMethod("/forecast"),
            PublicRoute.anyMethod("/report"),
            PublicRoute.anyMethod("/chat/summarize"),
            new PublicRoute(HttpMethod.OPTIONS, "/**"),
            new PublicRoute(HttpMethod.GET, "/user/avatar/**"),
            new PublicRoute(HttpMethod.GET, "/posts/*/images/**"));

    /** {@link #PUBLIC_ROUTES} as matchers, shared by both the JWT filter and {@code authorizeExchange}. */
    private static ServerWebExchangeMatcher[] publicRouteMatchers() {
        return PUBLIC_ROUTES.stream().map(PublicRoute::toMatcher).toArray(ServerWebExchangeMatcher[]::new);
    }

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
        // Derived from the SAME PUBLIC_ROUTES list used by authorizeExchange below, so the two can
        // never drift apart.
        ServerWebExchangeMatcher publicMatcher = ServerWebExchangeMatchers.matchers(publicRouteMatchers());
        jwtFilter.setRequiresAuthenticationMatcher(new NegatedServerWebExchangeMatcher(publicMatcher));

        http
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                .authorizeExchange(authorize -> authorize
                        .matchers(publicRouteMatchers()).permitAll()
                        .anyExchange().authenticated())
                .addFilterAt(jwtFilter, SecurityWebFiltersOrder.AUTHENTICATION)
                // Trả 401 thuần (không kèm WWW-Authenticate: Basic) để trình duyệt
                // không hiện hộp Sign in mặc định khi token JWT hết hạn / thiếu.
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(new HttpStatusServerEntryPoint(HttpStatus.UNAUTHORIZED)))
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource));

        return http.build();
    }
}
