package com.techpulse.techradar.shared.neo4j;

import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.function.Function;

/**
 * Shared boilerplate for read-only Neo4j queries. Every Neo4j-backed repository used to
 * hand-roll the same
 * {@code Mono.fromCallable(() -> { try (Session s = driver.session()) {...} }).subscribeOn(Schedulers.boundedElastic())}
 * idiom per method; this is the single shared implementation they should all delegate to instead.
 */
public final class Neo4jReadTemplate {

    private Neo4jReadTemplate() {
    }

    /**
     * Runs {@code work} against a fresh {@link Session} on {@link Schedulers#boundedElastic()},
     * deferred until subscription (so any exception {@code work} throws — including validation
     * errors thrown before any Cypher runs — surfaces as a reactive error rather than a
     * synchronous throw), and closes the session afterward.
     */
    public static <T> Mono<T> read(Driver driver, Function<Session, T> work) {
        return Mono.fromCallable(() -> {
            try (Session session = driver.session()) {
                return work.apply(session);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
