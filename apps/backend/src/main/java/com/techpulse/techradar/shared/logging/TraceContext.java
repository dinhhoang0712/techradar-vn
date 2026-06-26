package com.techpulse.techradar.shared.logging;

/**
 * Shared constants for distributed request tracing.
 * <p>
 * A single correlation id travels: inbound HTTP header -> Reactor Context -> SLF4J MDC ->
 * outbound calls to the Python services (same header). This lets one request be followed across
 * the gateway and both FastAPI services in aggregated logs.
 */
public final class TraceContext {

    /** Canonical correlation header used across the backend and the Python services. */
    public static final String HEADER = "X-Request-Id";

    /** Key under which the trace id is stored in both the Reactor Context and the SLF4J MDC. */
    public static final String MDC_KEY = "traceId";

    private TraceContext() {
    }
}
