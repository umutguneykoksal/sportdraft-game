# ADR-001: Redis Fallback to PostgreSQL

**Status:** Proposed  
**Date:** 2026-05-12  
**Context:** Active game sessions are stored solely in Redis (`ActiveSessionStore`). If Redis becomes unavailable, all in-flight sessions are lost.

## Problem

Redis is the single source of truth for hot session state. A Redis outage (connection failure, auth misconfiguration, OOM) causes `RedisConnectionFailureException` across all scheduled jobs and API calls, effectively bringing down the game.

## Decision (deferred)

When resilience becomes a priority, introduce a **dual-write architecture** with PostgreSQL JSONB as the durable fallback:

### Architecture

```
SessionStore (interface)
├── ActiveSessionStore        (current Redis impl, renamed to RedisSessionStore)
├── JdbcSessionStore          (new — stores same ActiveSession JSON in PostgreSQL)
└── ResilientSessionStore     (@Primary facade, delegates to both)
```

- **Write path:** PostgreSQL first (source of truth), then Redis (best-effort, catch failures).
- **Read path:** Redis first (fast), fall back to PostgreSQL on `RedisConnectionFailureException`.

### Implementation Steps

1. **Flyway migration** — `active_session_json` table with `id UUID PK`, `session_data JSONB`, `status VARCHAR(40)`, `created_at TIMESTAMPTZ`, indexed on `status` and `created_at`.

2. **Extract `SessionStore` interface** from `ActiveSessionStore` public methods (`save`, `saveIfVersionMatch`, `findById`, `getById`, `delete`, `exists`, `findWaitingSessions`, `findAllActiveIds`, `findByStatus`, `deleteAllActive`). Move `OptimisticLockException` and `SessionNotFoundException` to the interface.

3. **Rename `ActiveSessionStore` → `RedisSessionStore`**, annotate `@Component("redisSessionStore")`, implement `SessionStore`.

4. **Create `JdbcSessionStore`** — uses `JdbcTemplate` + same `redisObjectMapper` for JSON serialization. Upsert via `INSERT ... ON CONFLICT DO UPDATE`. Optimistic locking via `WHERE (session_data->>'version')::bigint = ?`.

5. **Create `ResilientSessionStore`** — `@Primary`, injects both stores by qualifier. Write methods always hit JDBC then best-effort Redis. Read methods try Redis first, catch `RedisConnectionFailureException`, fall back to JDBC with WARN log.

6. **Update consumers** — all services already depend on the concrete `ActiveSessionStore`; change to `SessionStore` interface. No logic changes needed.

### Why JSONB over normalized JPA mapping

`ActiveSession` is a denormalized aggregate (session + players + rounds + bids in one document). Mapping it back to `GameSession`/`SessionPlayer`/`AuctionRound`/`Bid` JPA entities would require complex bidirectional conversion. Storing the same JSON in a JSONB column reuses existing serialization with zero mapping code.

## Current Mitigation

- Ensure Redis credentials are correctly configured per environment (`application-railway.yml`, `application-production.yml`).
- Redis connection failures surface clearly in logs via the scheduled jobs' stack traces.
- Sessions are ephemeral (24h TTL); losing them is acceptable for MVP.

## When to Implement

- When the game has real concurrent users and session loss causes meaningful UX harm.
- When deploying to an environment where Redis reliability is not guaranteed.
