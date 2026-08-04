package com.techpulse.techradar.shared.http;

import com.techpulse.techradar.shared.exception.AppException;
import com.techpulse.techradar.shared.exception.DatabaseUnavailableException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Base class for adapter clients that call external Python microservices over HTTP.
 * <p>
 * Centralizes the retry/timeout/circuit-breaker/error-mapping tail that every {@code
 * Python*Client} adapter used to repeat by hand: an optional retry with backoff for idempotent
 * calls, a timeout, a circuit breaker (see {@code docs/adr/0007-circuit-breaker-for-python-service-calls.md}),
 * and mapping any transport failure to {@link DatabaseUnavailableException} — while letting an
 * {@link AppException} raised by a subclass's own {@code .onStatus(...)} handling (e.g. 404/409
 * mapping) pass through untouched instead of being swallowed and rewrapped.
 * <p>
 * The circuit breaker wraps the retry+timeout pipeline as a whole (not each individual retry
 * attempt), so it records exactly one success/failure per external call — and observes timeouts
 * as failures, since it subscribes to the already-timeout-decorated publisher.
 * <p>
 * WebClient construction (base URL + {@code X-Internal-Auth} header attachment) is <b>not</b>
 * duplicated here — subclasses build their client via {@code
 * com.techpulse.techradar.shared.client.PythonServiceWebClientFactory#build}, the single shared
 * code path for that concern. Base URL, timeout and internal-token values still live in
 * subclasses as {@code @Value}-injected fields since their property keys differ per Python
 * service, as does any response-type-specific or non-idempotent call handling.
 */
public abstract class AbstractPythonServiceClient {

    /** Retry + timeout + circuit-breaker + error-mapping tail for a {@link Mono}, fixed error message. */
    protected <T> Mono<T> mapMono(Mono<T> request, CircuitBreaker circuitBreaker, boolean retry,
                                   Duration timeout, String unavailableMessage) {
        return mapMono(request, circuitBreaker, retry, timeout, null, unavailableMessage);
    }

    /**
     * Same as above, plus a caller-supplied logging callback invoked (with the original,
     * unwrapped exception) on any failure that isn't already an {@link AppException}.
     */
    protected <T> Mono<T> mapMono(Mono<T> request, CircuitBreaker circuitBreaker, boolean retry, Duration timeout,
                                   Consumer<Throwable> onError, String unavailableMessage) {
        return mapMono(request, circuitBreaker, retry, timeout, onError, ex -> unavailableMessage);
    }

    /**
     * Same as above, but the {@link DatabaseUnavailableException} message is derived from the
     * originating exception (e.g. appending {@code ex.getMessage()}) rather than being fixed.
     */
    protected <T> Mono<T> mapMono(Mono<T> request, CircuitBreaker circuitBreaker, boolean retry, Duration timeout,
                                   Consumer<Throwable> onError, Function<Throwable, String> messageFn) {
        Mono<T> pipeline = retry ? request.retryWhen(Retry.backoff(3, Duration.ofSeconds(1))) : request;
        return pipeline
                .timeout(timeout)
                .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
                .onErrorResume(ex -> {
                    if (ex instanceof AppException) {
                        return Mono.error(ex);
                    }
                    if (ex instanceof CallNotPermittedException) {
                        return Mono.error(new DatabaseUnavailableException(
                                "Service temporarily unavailable (circuit breaker open): " + messageFn.apply(ex), ex));
                    }
                    if (onError != null) {
                        onError.accept(ex);
                    }
                    return Mono.error(new DatabaseUnavailableException(messageFn.apply(ex), ex));
                });
    }

    /** Retry + timeout + circuit-breaker + error-mapping tail for a {@link Flux}, fixed error message. */
    protected <T> Flux<T> mapFlux(Flux<T> request, CircuitBreaker circuitBreaker, boolean retry,
                                   Duration timeout, String unavailableMessage) {
        return mapFlux(request, circuitBreaker, retry, timeout, null, unavailableMessage);
    }

    /**
     * Same as above, plus a caller-supplied logging callback invoked (with the original,
     * unwrapped exception) on any failure that isn't already an {@link AppException}.
     */
    protected <T> Flux<T> mapFlux(Flux<T> request, CircuitBreaker circuitBreaker, boolean retry, Duration timeout,
                                   Consumer<Throwable> onError, String unavailableMessage) {
        return mapFlux(request, circuitBreaker, retry, timeout, onError, ex -> unavailableMessage);
    }

    /**
     * Same as above, but the {@link DatabaseUnavailableException} message is derived from the
     * originating exception rather than being fixed.
     */
    protected <T> Flux<T> mapFlux(Flux<T> request, CircuitBreaker circuitBreaker, boolean retry, Duration timeout,
                                   Consumer<Throwable> onError, Function<Throwable, String> messageFn) {
        Flux<T> pipeline = retry ? request.retryWhen(Retry.backoff(3, Duration.ofSeconds(1))) : request;
        return pipeline
                .timeout(timeout)
                .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
                .onErrorResume(ex -> {
                    if (ex instanceof AppException) {
                        return Flux.error(ex);
                    }
                    if (ex instanceof CallNotPermittedException) {
                        return Flux.error(new DatabaseUnavailableException(
                                "Service temporarily unavailable (circuit breaker open): " + messageFn.apply(ex), ex));
                    }
                    if (onError != null) {
                        onError.accept(ex);
                    }
                    return Flux.error(new DatabaseUnavailableException(messageFn.apply(ex), ex));
                });
    }
}
