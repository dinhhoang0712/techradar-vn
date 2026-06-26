package com.techpulse.techradar.shared.logging;

import org.springframework.boot.web.reactive.function.client.WebClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import reactor.core.publisher.Mono;

/**
 * Propagates the current request's trace id onto every outbound {@link org.springframework.web.reactive.function.client.WebClient}
 * call, so the Python services (ai-rag-core, ml-clustering) log the same {@link TraceContext#HEADER}
 * and a single request can be followed end-to-end.
 * <p>
 * Applied via {@link WebClientCustomizer}, it covers all clients built from the injected
 * {@code WebClient.Builder} (the three {@code Python*Client} adapters) without touching each one.
 */
@Configuration
public class TracePropagationWebClientConfig {

    @Bean
    public WebClientCustomizer tracePropagationCustomizer() {
        return builder -> builder.filter(ExchangeFilterFunction.ofRequestProcessor(request ->
                Mono.deferContextual(ctx -> {
                    if (ctx.hasKey(TraceContext.MDC_KEY)) {
                        String traceId = ctx.get(TraceContext.MDC_KEY);
                        return Mono.just(ClientRequest.from(request)
                                .header(TraceContext.HEADER, traceId)
                                .build());
                    }
                    return Mono.just(request);
                })));
    }
}
