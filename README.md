# Football Card Auction Game

![CI](https://github.com/umutguneykoksal/football-card-auction-game/actions/workflows/ci.yml/badge.svg)
![Java](https://img.shields.io/badge/Java-21-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3-green)
![React](https://img.shields.io/badge/React-18-61dafb)
![License](https://img.shields.io/badge/License-Proprietary-red)

Browser-based **multiplayer football card auction draft** game. Players bid on cards from a **100-credit** budget, fill an **11-slot formation**, then the server runs a **rating-weighted match simulation**.

> **Portfolio / demo project** — source is visible on GitHub for review purposes.  
> **Not open source.** See [LICENSE](LICENSE) — copying, forking for reuse, or redistribution without permission is not allowed.

---

## Highlights

- **3 game modes** — Duel (2p), Tournament (4p bracket), League (4p round-robin + playoff)
- **Server-authoritative** state — Redis live sessions + PostgreSQL persistence
- **Real-time** — WebSocket + SSE fallback, ordered event log + reconnect sync
- **Modular monolith** — BCE slices (`boundary`, `control`, `entity`) with ADRs
- **Strong test discipline** — JaCoCo 85% backend gate, Jest coverage gate, Testcontainers IT, Playwright E2E (3 modes)

---

## Game modes

| Mode | Players | Flow |
|------|---------|------|
| **Duel** | 2 | Lobby → formation → auction draft → instant match |
| **Tournament** | 4 | Draft → semi-finals → intermission → final → champion |
| **League** | 4 | Draft → 3 matchdays → standings → co-champion or playoff final |

---

## Stack

| Layer | Technology |
|-------|------------|
| Backend | Java 21, Spring Boot 3.3, JPA, Validation, Actuator |
| Data | PostgreSQL 16, Redis 7, Flyway |
| Frontend | React 18, TypeScript, TanStack Query, Zustand, Tailwind |
| Real-time | WebSocket (primary), SSE fallback, REST event sync |
| API | `/api/v2` canonical ([ADR-003](docs/adr/ADR-003-api-v2-migration.md)) |
| Testing | JUnit 5, Testcontainers, Jest, Playwright |
| Infra | Docker Compose (local), Railway (deploy) |

---

## Repository layout

```
backend/          Java modular monolith — com.example.auction.<feature>
frontend/         CRA app — src/features/ + src/shared/
e2e/              Playwright smoke tests (duel, tournament, league)
infra/            docker-compose.yml (Postgres 16, Redis 7)
docs/             ADRs, test coverage matrix, architecture notes
```

**Backend slices:** `lobby`, `session`, `bidding`, `simulation`, `competition`

**Frontend routes:** `/lobby`, `/auction/:sessionId`, `/match/:sessionId`, `/tournament/...`, `/league/...`, `/spectate/:sessionId`

---

## Quick start

### Prerequisites

- JDK 21, Maven 3.9+
- Node.js 20+
- Docker (for Postgres/Redis and integration tests)

### Run locally

```bash
# 1. Infrastructure
cd infra && docker compose up -d

# 2. Backend (http://localhost:8080)
cd backend && mvn spring-boot:run -Dspring-boot.run.profiles=local

# 3. Frontend (http://localhost:3000)
cd frontend && npm ci && npm start
```

Optional: `frontend/.env`

```env
REACT_APP_API_BASE=http://localhost:8080
```

### API v1 fallback

```env
REACT_APP_API_VERSION=v1
```

---

## Testing

| Layer | Command | Gate |
|-------|---------|------|
| Backend unit + IT | `cd backend && mvn verify` | JaCoCo **85%** line coverage |
| Backend unit only | `cd backend && mvn test -Pskip-it` | No Docker required |
| Frontend | `cd frontend && npm run test:coverage` | Jest thresholds 85/75/80/85 |
| E2E (stack running) | `cd e2e && E2E_BASE_URL=http://localhost:3000 npm test` | Duel + tournament + league smoke |

See [docs/test-coverage-matrix.md](docs/test-coverage-matrix.md) and [docs/test-coverage-backlog-phase5.md](docs/test-coverage-backlog-phase5.md).

---

## CI (GitHub Actions)

On every push/PR to `main`/`master`:

| Job | What runs |
|-----|-----------|
| **Backend** | `mvn verify` — unit + Testcontainers IT, JaCoCo 85% |
| **Frontend** | `npm run test:coverage` |
| **Playwright E2E** | Full stack smoke — duel, tournament, league |

---

## Architecture (short)

- **Redis** — active session state (`ActiveSession`), auction rounds, optimistic locking
- **PostgreSQL** — cards, competitions, match history, Flyway migrations
- **Domain events** — `ApplicationEventPublisher` + `@TransactionalEventListener(AFTER_COMMIT)` + idempotent `ProcessedEvent` handlers
- **Competition** — shared `CompetitionMatchCommandService` with league/tournament mode hooks
- **Auth (MVP)** — opaque UUID bearer token ([ADR-002](docs/adr/ADR-002-anonymous-player-identity.md))

Key ADRs: [docs/adr/](docs/adr/) — especially [ADR-005](docs/adr/ADR-005-game-mode-and-competition.md).

---

## Card pools

Host selects **league** + **minimum OVR** (70 / 75 / 80) at room creation. Thresholds require **≥ 100 eligible cards**.

| League | Status |
|--------|--------|
| Premier League | Available |
| Trendyol Super Lig | Available |
| La Liga, Serie A, Bundesliga | Coming soon |

Data pipeline: [docs/player-data-pipeline.md](docs/player-data-pipeline.md)

---

## Deploy

- **Backend + frontend:** Railway (see project infra config)
- **Local:** Docker Compose + Spring `local` profile + CRA build served statically

---

## Admin

Session delete endpoints require `X-Admin-Key` when `APP_ADMIN_API_KEY` is set.

---

## License & copyright

**Copyright © 2026 Umut Guney Koksal. All rights reserved.**

This project is **proprietary**. You may **not** copy, modify, distribute, or use the source code without explicit written permission from the copyright holder.

Public GitHub visibility is for **portfolio demonstration and code review only** — not a grant of reuse rights.

For licensing inquiries: [github.com/umutguneykoksal](https://github.com/umutguneykoksal)

Full terms: [LICENSE](LICENSE)
