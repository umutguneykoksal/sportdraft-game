# SportDraft

**[Play (production) →](https://www.sportdraft.dev)** · **[Dev / staging →](https://test.sportdraft.dev)**

| Environment | URL                                                |
| ----------- | -------------------------------------------------- |
| Production  | [sportdraft.dev](https://www.sportdraft.dev)       |
| Development | [test.sportdraft.dev](https://test.sportdraft.dev) |

Multiplayer football card auction draft. Two to four players share a card pool, bid from a **100-credit** budget, fill an **11-slot formation**, then the server runs an instant rating-based match simulation.

![CI](https://github.com/umutguneykoksal/sportdraft-game/actions/workflows/ci.yml/badge.svg)
![Java 21](https://img.shields.io/badge/Java-21-orange)
![Spring Boot 3.3](https://img.shields.io/badge/Spring%20Boot-3.3-green)
![React 18](https://img.shields.io/badge/React-18-61dafb)

---

## Game modes

| Mode           | Players | Flow                                                                        |
| -------------- | ------- | --------------------------------------------------------------------------- |
| **Duel**       | 2       | Lobby → formation → auction draft → instant match result                    |
| **Tournament** | 4       | Shared draft → semi-finals → intermission → final                           |
| **League**     | 4       | Shared draft → three matchdays → live table → co-champions or playoff final |

The host picks the **card pool** before the room opens: league (Premier League or Trendyol Super Lig) and minimum overall rating (**70 / 75 / 80**).

---

## How a session works

1. **Lobby** — host creates a room (`createRoom`) or players join from the list (`joinRoom`). When the room is **full** (2 in Duel, 4 in Tournament/League), a **5-second** pairing countdown runs, then formation selection opens.
2. **Formation (ready-up)** — each player picks a formation (e.g. 4-4-2, 4-3-3) and taps **Start Auction**. Until everyone has confirmed, the UI shows **Ready (X/N)** with **no** auction timer. When **all** players in the room are ready, a **5-second** countdown runs, then the draft starts. Formations are locked after you confirm; opponents can still pick until they confirm.
3. **Auction draft** — sequential rounds; bid on cards that fit open roster slots. Budget, turn order, anti-snipe extension, and auto-pass rules are enforced server-side.
4. **Match** — when the draft finishes, the server simulates the fixture from card ratings and publishes the score (and event timeline for duels). Tournament and league modes continue with bracket or season fixtures after the shared draft.

All game state is **server-authoritative**: clients send commands and render what the server sends back. Disconnect and reconnect are handled with a live event log (WebSocket primary, SSE fallback, REST event sync).

---

## What's live today

- **Three game modes** — duel, tournament bracket, league season
- **Real-time updates** — WebSocket primary, SSE fallback
- **Spectator mode** — watch in-progress rooms from the lobby
- **Anonymous play** — no account required; session token in the browser
- **In-app feedback** — bug reports and suggestions from the lobby
- **Card pools** — Premier League and Trendyol Super Lig

Football only for now.

---

## Roadmap

### Next

- [ ] **User accounts** — OAuth sign-in instead of anonymous browser tokens
- [ ] **Basketball** — second sport mode
- [ ] **More card pools** — additional leagues and rating tiers
- [ ] **Abuse protection** — rate limits on bids, room creation, and feedback

### Planned

- [ ] **Match history & profiles** — browse past results and stats
- [ ] **Spectator improvements** — richer live view and replay UX
- [ ] **Full-draft E2E coverage** — automated end-to-end tests for complete 44-round drafts (smoke tests use shorter drafts today)
- [ ] **Multi-instance backend** — scheduler coordination when running more than one app replica

### Recently shipped

- Redis-backed live auction state (single aggregate per session)
- Room create/join flow (`createRoom` + `joinRoom` on `/api/v2`)
- Formation ready-up: partial **Ready (X/N)**, **5s** countdown only when all players are ready
- Legacy API cleanup and schema migration (Flyway V26)

---

## For developers

This repo is the full stack for [sportdraft.dev](https://www.sportdraft.dev): Spring Boot API, React SPA, Docker Compose for local infra, GitHub Actions CI, Railway production deploy.

### Architecture

```mermaid
flowchart TB
  Client["React SPA · sportdraft.dev"]

  Client -->|"REST — commands (bid, join, formation)"| API
  Client <-->|"WebSocket / SSE — live updates"| API

  subgraph API["Spring Boot · dev.sportdraft"]
    direction LR
    lobby[lobby] --- session[session] --- bidding[bidding] --- simulation[simulation]
    bidding --- competition[competition]
  end

  API --> Redis[("Redis<br/>active sessions · auction state · locks")]
  API --> PG[("PostgreSQL<br/>cards · competitions · history")]

  style Client fill:#1e293b,stroke:#475569,color:#f8fafc
  style API fill:#0f172a,stroke:#334155,color:#e2e8f0
  style Redis fill:#7f1d1d,stroke:#991b1b,color:#fecaca
  style PG fill:#1e3a5f,stroke:#1d4ed8,color:#bfdbfe
```

The backend is a **modular monolith**: feature slices (`lobby`, `session`, `bidding`, `simulation`, `competition`) each with boundary / control / entity layers. Active draft and auction state lives in **Redis**; Postgres holds the card catalog, competition brackets, and match history.

Published architecture reference (also on GitHub): [docs/backend-architecture.md](docs/backend-architecture.md).

**API:** canonical surface is `/api/v2` (e.g. `/api/v2/lobbies`, `/api/v2/sessions/{id}/…`). Legacy `/api` endpoints remain as a deprecated fallback.

**Frontend:** Create React App, feature folders under `src/features/`, shared hooks (`useSessionSync`, `useRealtimeSession`) — not a separate BFF; the SPA talks to the API directly.

**Public repo access:** PRs can be limited to **collaborators only**; `main` / `develop` are protected via rulesets. Public repos cannot disable the Fork button on GitHub. See [.github/REPOSITORY_ACCESS.md](.github/REPOSITORY_ACCESS.md).

### Stack

Java 21 · Spring Boot 3.3 · PostgreSQL 16 · Redis 7 · Flyway · React 18 · TypeScript · Tailwind · JUnit 5 · Testcontainers · Jest · Playwright

### Run locally

JDK 21, Node 20+, Docker.

```bash
cd infra && docker compose up -d
cd backend && mvn spring-boot:run -Dspring-boot.run.profiles=local
cd frontend && npm ci && npm start
```

Frontend defaults to `http://localhost:8080`. Override with `REACT_APP_API_BASE` in `frontend/.env`.

```
backend/     API + domain logic (BCE feature slices)
frontend/    React SPA
e2e/         Playwright smoke tests (see e2e/README.md)
infra/       docker-compose (Postgres + Redis, healthchecks)
docs/        backend-architecture.md is published; other notes may be local-only
```

### Tests & CI

Every push runs backend (`mvn verify` with Testcontainers, JaCoCo line coverage gate at 85% on the control layer), frontend (`npm run test:coverage`), and Playwright smoke tests for all three game modes.

```bash
cd backend && mvn verify          # needs Docker
cd backend && mvn test -Pskip-it    # unit tests only
cd frontend && npm run test:coverage
cd e2e && npm ci && npm test      # see e2e/README.md
```

**E2E locally:** set `E2E_BASE_URL` to a running app (e.g. `http://localhost:3000` with backend on `:8080`). If unset, smoke tests are skipped. CI sets `E2E_BASE_URL` to the staged or production URL. Smoke scope today: lobby → formation → auction entry per mode (not a full 44-round draft).

---

## License

**Proprietary** — source is published for transparency; reading the code does not grant a license to use, copy, or redistribute it. See [LICENSE](LICENSE).

Football player names, ratings, and club data may be subject to third-party rights; this project does not grant rights to redistribute that data.

Copyright © 2026 [Umut Guney Koksal](https://github.com/umutguneykoksal). For permissions, contact via GitHub.
