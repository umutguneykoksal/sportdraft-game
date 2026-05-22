# SportDraft

**Play:** [sportdraft.dev](https://www.sportdraft.dev)

Multiplayer football card auction draft. Two to four players bid from a 100-credit budget, pick an 11-slot formation, then the server simulates the match from card ratings.

Duel (2p), tournament bracket (4p), and league with standings + optional playoff (4p) are all live on the site.

Built and maintained by [Umut Guney Koksal](https://github.com/umutguneykoksal).

![CI](https://github.com/umutguneykoksal/sportdraft-game/actions/workflows/ci.yml/badge.svg)
![Java 21](https://img.shields.io/badge/Java-21-orange)
![Spring Boot 3.3](https://img.shields.io/badge/Spring%20Boot-3.3-green)
![React 18](https://img.shields.io/badge/React-18-61dafb)

> **Proprietary** — [LICENSE](LICENSE). No reuse without permission.

---

## How it works

**Duel** — lobby, formation pick, auction draft, instant result.

**Tournament** — shared draft, semi-finals, intermission, final.

**League** — shared draft, three matchdays, live table, co-champions or a playoff final.

Card pool: host picks league and minimum OVR (70 / 75 / 80). Premier League and Trendyol Super Lig are in; more leagues later.

---

## Architecture

```
React  ──REST──►  Spring Boot  ──►  PostgreSQL
  │                    │
  └── WS / SSE ◄───────┴──►  Redis (live sessions, auction state)
```

Backend package root: `dev.sportdraft` — feature slices `lobby`, `session`, `bidding`, `simulation`, `competition` (boundary / control / entity).

State is server-side: Redis holds active sessions and auction rounds (with optimistic locking), Postgres holds cards, competitions, and history. Clients send commands and render what the server sends back.

Real-time updates go over WebSocket with SSE as fallback; reconnect pulls from the ordered event log.

Competition flows (league vs tournament) share the same match orchestration with mode-specific rules. Cross-cutting side effects use Spring domain events after commit, with idempotent handlers so retries are safe.

Auth is an anonymous session token for now (Bearer on REST, query param on the stream).

---

## Stack

Java 21, Spring Boot 3.3, PostgreSQL 16, Redis 7, Flyway · React 18, TypeScript, TanStack Query, Zustand, Tailwind · JUnit 5, Testcontainers, Jest, Playwright · GitHub Actions · hosted on Railway at [sportdraft.dev](https://www.sportdraft.dev).

API surface is `/api/v2`.

---

## Tests & CI

Every push runs backend (`mvn verify` with Testcontainers, JaCoCo line coverage at 85%), frontend (`npm run test:coverage`), and Playwright smoke tests for all three game modes.

Locally:

```bash
cd backend && mvn verify                 # needs Docker
cd backend && mvn test -Pskip-it           # unit only
cd frontend && npm run test:coverage
```

---

## Run locally

JDK 21, Node 20+, Docker.

```bash
cd infra && docker compose up -d
cd backend && mvn spring-boot:run -Dspring-boot.run.profiles=local
cd frontend && npm ci && npm start
```

Frontend talks to `http://localhost:8080` by default. Override with `REACT_APP_API_BASE` in `frontend/.env`.

```
backend/     API + domain logic
frontend/    web app
e2e/         Playwright
infra/       docker-compose (Postgres + Redis)
```

---

## Not shipped yet

Accounts (OAuth), rate limiting, multi-region. Timed phase jobs assume a single production replica. E2E smoke tests use short drafts; full 44-round flows are in unit/IT tests.

---

## License

Copyright © 2026 Umut Guney Koksal. All rights reserved.

See [LICENSE](LICENSE). For permissions: [github.com/umutguneykoksal](https://github.com/umutguneykoksal).
