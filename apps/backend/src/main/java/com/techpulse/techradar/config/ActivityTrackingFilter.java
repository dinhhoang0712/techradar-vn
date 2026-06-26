package com.techpulse.techradar.config;

import com.techpulse.techradar.config.JwtTokenProvider;
import com.techpulse.techradar.features.system.ports.ActivityLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Set;

/**
 * Records lightweight traffic ("visit") and search events into {@code activity_log} so the admin
 * dashboard shows real metrics. Recording is fire-and-forget and never blocks/fails the request.
 * <p>
 * Only SUCCESSFUL (2xx) requests are counted (so 401/404/polling-error noise is excluded), and the
 * real user id is captured from the Bearer token when present. Paths here have NO {@code /api/v1}
 * prefix — {@code spring.webflux.base-path} is stripped before WebFilters run.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
@RequiredArgsConstructor
public class ActivityTrackingFilter implements WebFilter {

    private static final Set<String> SEARCH_PATHS = Set.of("/radar/search", "/compare/search", "/graph/explore");
    private static final Set<String> IGNORED_PREFIXES = Set.of(
            "/status", "/health", "/actuator", "/v3/api-docs", "/swagger", "/webjars", "/favicon",
            "/admin/dashboard", "/user/avatar"); // don't count dashboard polling or image fetches

    private final ActivityLogRepository activityLog;
    private final JwtTokenProvider jwtTokenProvider;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        HttpMethod method = exchange.getRequest().getMethod();

        if (HttpMethod.OPTIONS.equals(method) || isIgnored(path)) {
            return chain.filter(exchange);
        }

        return chain.filter(exchange).doOnSuccess(v -> {
            HttpStatusCode status = exchange.getResponse().getStatusCode();
            if (status == null || !status.is2xxSuccessful()) {
                return; // only count successful requests
            }
            String userId = userIdFromAuth(exchange);
            activityLog.recordVisit(userId, path).onErrorComplete().subscribe();

            if (SEARCH_PATHS.contains(path)) {
                List<String> keywords = exchange.getRequest().getQueryParams().get("keywords");
                if (keywords != null) {
                    keywords.forEach(kw -> activityLog.recordSearch(kw).onErrorComplete().subscribe());
                }
            }
        });
    }

    private String userIdFromAuth(ServerWebExchange exchange) {
        String header = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith("Bearer ")) {
            return null;
        }
        String token = header.substring(7).trim();
        if (token.isEmpty() || !jwtTokenProvider.isTokenValid(token) || !jwtTokenProvider.isAccessToken(token)) {
            return null;
        }
        return jwtTokenProvider.getUserIdFromToken(token);
    }

    private boolean isIgnored(String path) {
        for (String prefix : IGNORED_PREFIXES) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
