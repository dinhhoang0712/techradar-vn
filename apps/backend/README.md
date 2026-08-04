# TechRadar Backend — API Gateway

Spring Boot 3.4 / Java 21 / WebFlux reactive API gateway. Serves the React web/mobile clients,
proxies AI requests to `services/ai-rag-core` and `services/ml-clustering`, and owns the
PostgreSQL schema (via Flyway) plus the real-time write path into the Neo4j knowledge graph.

For the system-wide picture (how this service fits with the Python AI services, the Kafka event
bus, and the data platform), start at [`docs/ARCHITECTURE.md`](../../docs/ARCHITECTURE.md) — this
README only covers what's specific to working inside `apps/backend`.

## Architecture at a glance

- **Hexagonal, feature-based**: every business capability lives under
  `features/{name}/{domain,ports,application,adapters/{input,output}}`. See
  [ADR-0001](../../docs/adr/0001-hexagonal-feature-modules.md) for the exact layering rules and
  why `aiproxy` is a deliberate exception.
- **Reactive end to end**: WebFlux + R2DBC (Postgres) + the async Neo4j driver — no blocking I/O
  on the request path. See [ADR-0002](../../docs/adr/0002-webflux-reactive-stack.md).
- **`shared/`** holds cross-feature infrastructure only (the `ApiResponse` envelope, Redis
  cache/rate-limit helpers, the Neo4j read template, the transactional outbox) — never
  feature-specific logic.
- **Decisions worth reading before changing behavior that looks inconsistent**: see
  [`docs/adr/`](../../docs/adr/) — in particular why `/auth/*` and `/status` don't use the
  `ApiResponse` envelope ([ADR-0003](../../docs/adr/0003-api-envelope-with-auth-exception.md)),
  why SSE fan-out uses Redis Pub/Sub instead of Kafka
  ([ADR-0004](../../docs/adr/0004-redis-pubsub-sse-fanout.md)), and how the transactional outbox
  guarantees `trend.alerts` delivery ([ADR-0005](../../docs/adr/0005-transactional-outbox-trend-alerts.md)).

## Running locally

```bash
# Postgres + Neo4j + Redis (from repo root)
docker compose up postgres neo4j redis

cd apps/backend
mvn spring-boot:run
```

There's no Maven wrapper in this module — Maven must already be on your `PATH`. Flyway migrates
the schema automatically on startup; no manual migration step is needed.

- API base path: `http://localhost:8080/api/v1`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`
- Health: `http://localhost:8080/api/v1/health`

## Testing

```bash
mvn test
```

Integration tests (`*IntegrationTest`, base class `IntegrationTestSupport`) start real
Postgres/Neo4j/Redis via Testcontainers automatically — Docker just needs to be runnable on the
machine, no `docker compose up` or env vars required first. Redis cross-instance pub/sub tests
(`*RedisCrossInstanceTest`) are the one exception: they need `REDIS_HOST` pointed at a real Redis,
since they spin up two independent Spring contexts against the same instance to prove fan-out
works across instances.

See [`docs/DEVELOPMENT_GUIDE.md`](../../docs/DEVELOPMENT_GUIDE.md) for the broader dev workflow
and [`docs/BACKEND_GUIDE.md`](../../docs/BACKEND_GUIDE.md) for feature-by-feature implementation
notes.

## Conventions to know before your first PR

- **Response envelope**: every endpoint wraps its success/error body in `shared.dto.ApiResponse`
  *except* `/auth/login`, `/auth/register`, `/auth/refresh`, `/auth/me`, and `/status` — those
  return the payload bare on purpose (see ADR-0003). Don't "fix" that inconsistency; don't extend
  it to new endpoints either.
- **snake_case everywhere**: Jackson is configured globally for `SNAKE_CASE` — don't add
  per-field `@JsonProperty` unless the field name has no case boundary for Jackson to split on.
- **RBAC by permission code, not role name**: gate admin endpoints on a permission
  (`@PreAuthorize` checking a code like `social:moderate`), never on `hasRole('ADMIN')` directly —
  see [ADR-0006](../../docs/adr/0006-permission-based-rbac.md).
- **R2DBC `bindNull`**: `.bind(name, null)` throws — use `shared.db.R2dbcBinders.bindNullable(spec,
  name, value[, type])` for nullable columns, not a locally re-written ternary (see ADR-0011).
- **New per-feature rate limiter?** Delegate the actual INCR+EXPIRE to
  `shared.redis.FixedWindowRateLimiter.isAllowed(redisTemplate, key, max, windowSeconds)` — don't
  hand-roll the counter logic again (see ADR-0011).
- **New SSE endpoint?** Wrap the event `Flux` with `shared.sse.SseHeartbeat.merge(events)` — a
  stream without a heartbeat can get silently killed by an idle-connection timeout somewhere in
  front of it (see ADR-0011, which fixes exactly that bug in `ConversationController`).
- **New Postgres-sourced event that must survive a crash before Kafka publish?** Use the
  transactional outbox (`shared.outbox`), not a direct fire-and-forget
  `KafkaProducerService.send()` — see ADR-0005 for when this applies (Postgres-sourced writes
  only; it doesn't help for Neo4j-sourced events like `job.match.alerts`).
- **Request field limited to a fixed vocabulary** (a status/type/tier-like string): add an enum
  next to the domain entity that owns it and annotate the DTO field with
  `@OneOf(TheEnum.class)` (`shared.validation`) — don't inline a `String[]` of allowed values or
  skip validation and rely on the DB `CHECK` alone. See ADR-0010.
