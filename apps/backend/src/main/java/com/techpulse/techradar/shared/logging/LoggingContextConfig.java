package com.techpulse.techradar.shared.logging;

import io.micrometer.context.ContextRegistry;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Hooks;

/**
 * Enables automatic propagation of the trace id across the reactive pipeline.
 * <p>
 * {@link Hooks#enableAutomaticContextPropagation()} makes Reactor copy registered context values
 * into the corresponding {@code ThreadLocal} (here, the MDC) around every operator, so log lines
 * emitted deep inside a reactive chain still carry the request's {@code traceId}.
 */
@Configuration
public class LoggingContextConfig {

    @PostConstruct
    void init() {
        Hooks.enableAutomaticContextPropagation();
        ContextRegistry.getInstance().registerThreadLocalAccessor(new MdcThreadLocalAccessor());
    }
}
