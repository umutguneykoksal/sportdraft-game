# SportDraft Backend — Architecture & Development Plan

Live product: [sportdraft.dev](https://www.sportdraft.dev) · Staging: [test.sportdraft.dev](https://test.sportdraft.dev)

---

## 1. What design do we use?

**Modular monolith + feature slice (BCE) + server-authoritative real-time game.**

This is not a microservice deployment; it is a single Spring Boot application. The domain is split into **feature slices**, each with boundary / control / entity layers for its own responsibility.

```
dev.sportdraft.<feature>.{boundary, control, entity}
```

| Slice | Responsibility |
|---|---|
| `lobby/` | Room list, `createRoom` / `joinRoom`, pairing + formation countdown, ready-up |
| `session/` | Redis session store, disconnect cleanup, archive |
| `bidding/` | Auction rounds, bids, settlement |
| `simulation/` | Rating-weighted match simulation |
| `competition/` | Tournament / league bracket, fixtures, replay rules |
| `common/` | SSE/WS, auth filter, idempotency, event log |

**Boundary** = HTTP/WS entry points (controllers, DTO records).  
**Control** = domain logic and use cases (`@Service`, listeners, schedulers).  
**Entity** = persistent models (JPA entities, Redis aggregates, repositories).

This is not full DDD; in practice it is **vertical slice + layered boundary** — readable for a small team, testable, single deploy unit.

---

## 2. Data and state model

### Active game (Redis)

During draft and auction, all state lives in **one JSON aggregate**:

```
session:{uuid}  →  ActiveSession
  ├── players, status, formations
  ├── rounds[], bids (embedded)
  └── cardPool, scores (in-memory after match)
```

- **Optimistic lock**: `version` field + `saveIfVersionMatch`
- **Per-session Redis lock**: `SessionLock.run()` — HTTP and schedulers must not race on the same session
- **Index SET/ZSET**: `sessions:active`, `sessions:waiting`, `sessions:status:*`
- **TTL**: 24 hours (refreshed while the session stays active)

### Persistent data (PostgreSQL)

| Table / area | Purpose |
|---|---|
| `cards` | Card catalog |
| `competition_*` | Tournament/league bracket, matches, watch state |
| `match_history` | Finished duel replay JSON |
| `processed_event` | Idempotent domain event handlers |

> **Note:** Legacy JPA `game_session` / `auction_round` / `bid` tables were removed in Flyway V26. **The runtime auction path is Redis.**

---

## 3. Real-time and command flow

```
Client  ──REST──►  boundary (command)
                    │
                    ▼
              control (LobbyService, AuctionService, …)
                    │
         SessionLock.run(sessionId)
                    │
                    ▼
              ActiveSessionStore (Redis)
                    │
         ApplicationEvent (after commit)
                    │
                    ▼
              listener (simulation, competition)
                    │
         SSE / WebSocket broadcast
                    │
                    ▼
              Client (render server state)
```

- **Commands** via REST (`/api/v2`)
- **Live updates** WebSocket primary, SSE fallback
- **Reconnect** ordered event log (`SessionEventLog` ring buffer)
- **Cross-slice decoupling** Spring `ApplicationEventPublisher` + `@EventListener` (most handlers) + `ProcessedEvent` idempotency

---

## 3.1 Lobby and formation (ready-up)

Session phases (`ActiveSession.status` in Redis):

```
WAITING → COUNTDOWN → FORMATION_SELECT → AUCTIONING → …
```

| Phase | Trigger | Duration / rule |
|---|---|---|
| **COUNTDOWN** | Room full (`playerCount == maxPlayers`) | **5s** pairing (`LOBBY_PAIRING_COUNTDOWN_SECONDS`) |
| **FORMATION_SELECT** | Pairing countdown finished | Pick formation + `confirmFormationReady` |
| **Auction start countdown** | `matchStartReadyCount == maxPlayers` (2 or 4) | **5s** (`FORMATION_ALL_READY_COUNTDOWN_SECONDS`) |
| **AUCTIONING** | Auction countdown expired or scheduler | Shuffle card pool, open first round |

**Ready-up rules** (`LobbyService.confirmFormationReady`):

1. Ready is rejected without a selected formation (422).
2. Formation cannot be changed after ready.
3. Partial ready (e.g. 1/2, 2/4): **no timer**; SSE `formation_ready` carries `readyCount` only.
4. All players ready: set `countdownEndsAt`, SSE `formation_ready` with countdown; `LobbyPhaseScheduler` moves to `AUCTIONING` when time expires.
5. Players who never confirmed ready get default **4-4-2** when the auction starts.

**API (v2):**

- `POST /api/v2/sessions/{id}/formation` — select formation
- `POST /api/v2/sessions/{id}/confirm-formation-ready` — Start Auction / ready

**Frontend:** `frontend/src/shared/lobbyTiming.ts` — `inferCountdownPhase` shows the auction countdown only when `matchStartReadyCount >= maxPlayers`; `LobbySessionPanel` shows Ready (X/N) in the footer.

---

## 4. Game modes

| Mode | Flow |
|---|---|
| **DUEL** (2p) | Lobby (5s pairing + formation ready-up + 5s all-ready) → draft → `SessionFinalizedEvent` → sim → score |
| **TOURNAMENT** (4p) | Same lobby → shared draft → `DraftCompletedEvent` → semi/final bracket |
| **LEAGUE** (4p) | Same lobby → shared draft → 3 matchdays → table → optional playoff |

Mode differences are isolated in `CompetitionModeBehavior` implementations (`LeagueCompetitionMode`, `TournamentCompetitionMode`).

---

## 5. Naming guide (post-refactor)

Generic / AI-style names were cleaned up. Preferred patterns:

| Old (removed) | New |
|---|---|
| `SessionOrchestrator` | Removed — controller → service directly |
| `SessionMutationGuard.mutate()` | `SessionLock.run()` / `runVoid()` |
| `AuctionTurnNormalizer` | `TurnSkipResolver.skipInactiveTurns()` |
| `CompetitionMatchModeHooks` | `CompetitionModeBehavior` |
| `TournamentPhaseCoordinator` | `TournamentBracketFlow` |
| `LeaguePhaseCoordinator` | `LeagueSeasonFlow` |
| `ensureOpenRound()` | `openRoundIfNeeded()` |
| `useAuctionOrchestrator` (FE) | `useSessionSync` + `useAuctionPageState` |

**Use domain verbs:** `createRoom`, `joinRoom`, `placeBid`, `closeRound`, `archive` — reserve `handle*`, `process*`, `ensure*` for cases that truly need them.

---

## 6. Test strategy

- **Unit**: Mockito, control layer
- **Slice**: `@WebMvcTest` controllers + exception handler
- **Integration**: `@SpringBootTest` + Testcontainers (Postgres + Redis)
- **E2E**: Playwright (smoke for all three modes)
- **Coverage gate**: JaCoCo 85% line coverage (control layer)

---

## 7. How to describe it (e.g. CV / portfolio)

**SportDraft** — multiplayer football card auction draft ([sportdraft.dev](https://www.sportdraft.dev))

- Java 21 / Spring Boot modular monolith (BCE feature slices)
- Server-authoritative game state in Redis; Postgres for competitions and history
- Real-time WebSocket/SSE; optimistic locking + per-session Redis lock
- Domain events with idempotent handlers; Testcontainers + 85% coverage gate
- 3 game modes (duel, tournament, league); deployed on Railway

**Do not claim:** microservices, Kafka, full OTEL/Grafana stack, or JPA-backed live auction state.

---

## 8. Development plan

### Phase A — Codebase hygiene (short term)

- [x] AI/generic name refactor (`SessionLock`, `CompetitionModeBehavior`, …)
- [x] Remove redundant `SessionOrchestrator`
- [x] Unused JPA auction entity/repo cleanup + Flyway V26 table drop
- [x] Orphan Redis index reconciliation (TTL expire edge case)
- [x] Deprecated `joinLobby` endpoint removed (backend + frontend)

### Phase B — Product / auth (medium term)

- [ ] OAuth / accounts (replace anonymous browser tokens)
- [ ] Rate limiting (bid, feedback, room create)
- [ ] One user = one active lobby guard
- [ ] Multi-replica: scheduler leader election or Redis-based tick

### Phase C — Quality and operations (medium term)

- [ ] `sessionId` correlation in Railway logs (extend MDC)
- [ ] Health check: Redis + Postgres deep check in production
- [ ] Load test: concurrent rooms + auction bid latency
- [ ] E2E: full 44-round draft scenario (smoke tests are shorter today)

### Phase D — Expansion (long term)

- [ ] Basketball / second sport mode
- [ ] Additional league pools
- [ ] Spectator improvements
- [ ] Match history search / profile page

---

## 9. Run locally

```bash
cd infra && docker compose up -d
cd backend && mvn spring-boot:run -Dspring-boot.run.profiles=local
cd frontend && npm start
```

Tests:

```bash
cd backend && mvn verify          # Testcontainers — requires Docker
cd backend && mvn test -Pskip-it    # unit tests only
```

See root [README.md](../README.md) and [e2e/README.md](../e2e/README.md) for frontend and Playwright setup.

---

## 10. Folder reference

```
backend/src/main/java/dev/sportdraft/
├── lobby/boundary/          LobbyCommandController, DTOs
├── lobby/control/           LobbyService, LobbyPhaseScheduler
├── session/redis/           ActiveSession, ActiveSessionStore, SessionLock
├── session/control/         SessionArchiver, disconnect scheduler
├── bidding/control/         AuctionService, TurnSkipResolver, settlement
├── simulation/control/      MatchSimulator, event listeners
├── competition/control/     LeagueSeasonFlow, TournamentBracketFlow
└── common/                  SSE, WS, web filters, event log
```

Last updated: 2026-05-24
