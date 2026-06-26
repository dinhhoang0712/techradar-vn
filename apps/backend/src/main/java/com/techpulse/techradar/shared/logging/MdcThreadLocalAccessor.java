package com.techpulse.techradar.shared.logging;

import io.micrometer.context.ThreadLocalAccessor;
import org.slf4j.MDC;

/**
 * Bridges the trace id between the Reactor {@code Context} and the SLF4J {@code MDC}.
 * <p>
 * With {@code Hooks.enableAutomaticContextPropagation()} (see {@link LoggingContextConfig}), Reactor
 * restores the value stored under {@link TraceContext#MDC_KEY} into the MDC of whatever thread runs
 * each operator. That is what makes {@code [%X{traceId}]} resolve correctly in the reactive stack,
 * where work hops between threads.
 */
public class MdcThreadLocalAccessor implements ThreadLocalAccessor<String> {

    @Override
    public Object key() {
        return TraceContext.MDC_KEY;
    }

    @Override
    public String getValue() {
        return MDC.get(TraceContext.MDC_KEY);
    }

    @Override
    public void setValue(String value) {
        MDC.put(TraceContext.MDC_KEY, value);
    }

    @Override
    public void setValue() {
        MDC.remove(TraceContext.MDC_KEY);
    }

    @Override
    public void restore() {
        MDC.remove(TraceContext.MDC_KEY);
    }
}
