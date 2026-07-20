package com.techpulse.techradar.config;

import com.techpulse.techradar.features.system.ports.ActivityLogRepository;
import com.techpulse.techradar.shared.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.util.context.ContextView;

import java.util.List;
import java.util.Set;

/**
 * Records lightweight traffic ("visit") and search events into {@code activity_log} so the admin
 * dashboard shows real metrics. Recording is fire-and-forget and never blocks/fails the request.
 * <p>
 * Only SUCCESSFUL (2xx) requests are counted (so 401/404/polling-error noise is excluded), and the
 * real user id is read from the already-authenticated {@code SecurityContext} when present. Paths
 * here have NO {@code /api/v1} prefix — {@code spring.webflux.base-path} is stripped before
 * WebFilters run.
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

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        HttpMethod method = exchange.getRequest().getMethod();

        if (HttpMethod.OPTIONS.equals(method) || isIgnored(path)) {
            return chain.filter(exchange);
        }

        // doOnEach (rather than doOnSuccess) so we can capture the Reactor ContextView carrying the
        // authenticated SecurityContext that Spring Security's AuthenticationWebFilter wrote further
        // upstream; a plain doOnSuccess callback has no access to that context.
        return chain.filter(exchange).doOnEach(signal -> {
            if (!signal.isOnComplete()) {
                return;
            }
            HttpStatusCode status = exchange.getResponse().getStatusCode();
            if (status == null || !status.is2xxSuccessful()) {
                return; // only count successful requests
            }
            recordActivity(exchange, path, signal.getContextView());
        });
    }

    private void recordActivity(ServerWebExchange exchange, String path, ContextView contextView) {
        // Fire-and-forget: never blocks or fails the request. The authenticated user id (if any) is
        // read from the already-populated security context instead of re-parsing the Bearer token.
        SecurityUtils.currentUserId()
                .flatMap(userId -> activityLog.recordVisit(userId, path))
                .switchIfEmpty(Mono.defer(() -> activityLog.recordVisit(null, path)))
                .contextWrite(contextView)
                .onErrorComplete()
                .subscribe();

        if (SEARCH_PATHS.contains(path)) {
            List<String> keywords = exchange.getRequest().getQueryParams().get("keywords");
            if (keywords != null) {
                keywords.forEach(kw -> activityLog.recordSearch(kw).onErrorComplete().subscribe());
            }
        }
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
