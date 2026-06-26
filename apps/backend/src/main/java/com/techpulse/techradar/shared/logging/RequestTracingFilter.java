package com.techpulse.techradar.shared.logging;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Establishes a per-request trace id and emits one structured access log line per request.
 * <p>
 * Runs first ({@link Ordered#HIGHEST_PRECEDENCE}) so every downstream filter, controller and use
 * case — and the outbound calls to the Python services — share the same id. The id is taken from
 * the inbound {@link TraceContext#HEADER} when present (e.g. set by an upstream gateway) and
 * generated otherwise. It is written to the Reactor Context (for reactive propagation into MDC),
 * echoed back on the response header, and used to tag the access line.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestTracingFilter implements WebFilter {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String incoming = exchange.getRequest().getHeaders().getFirst(TraceContext.HEADER);
        String traceId = (incoming != null && !incoming.isBlank())
                ? incoming.trim()
                : UUID.randomUUID().toString().replace("-", "").substring(0, 16);

        exchange.getResponse().getHeaders().set(TraceContext.HEADER, traceId);

        String method = exchange.getRequest().getMethod().name();
        String path = exchange.getRequest().getPath().value();
        long startNanos = System.nanoTime();

        return chain.filter(exchange)
                .doFinally(signal -> logAccess(exchange, traceId, method, path, startNanos))
                .contextWrite(ctx -> ctx.put(TraceContext.MDC_KEY, traceId));
    }

    private void logAccess(ServerWebExchange exchange, String traceId, String method, String path, long startNanos) {
        long millis = (System.nanoTime() - startNanos) / 1_000_000;
        HttpStatusCode status = exchange.getResponse().getStatusCode();
        int code = status != null ? status.value() : 0;

        // doFinally may run on a thread without the propagated MDC, so set it explicitly here.
        MDC.put(TraceContext.MDC_KEY, traceId);
        try {
            if (code >= 500) {
                log.error("{} {} -> {} ({} ms)", method, path, code, millis);
            } else if (code >= 400) {
                log.warn("{} {} -> {} ({} ms)", method, path, code, millis);
            } else {
                log.info("{} {} -> {} ({} ms)", method, path, code, millis);
            }
        } finally {
            MDC.remove(TraceContext.MDC_KEY);
        }
    }
}
