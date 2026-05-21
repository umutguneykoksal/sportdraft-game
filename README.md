# Football Card Auction Game

Browser-based football card auction draft game. Players bid on cards from a **100-credit** budget, fill an **11-slot formation**, then the server runs a rating-weighted match simulation.

Supported formats:

| Mode | Players | Status |
|------|---------|--------|
| **Duel** | 2 | Full flow (lobby → draft → match) |
| **Tournament** | 4 | Draft + bracket; semi/final matches with replay |
| **League** | 4 | Draft only; standings/fixtures planned |

## Stack

- **Backend**: Java 21, Spring Boot 3.3 (Web, JPA, Validation, Actuator), PostgreSQL, Redis, Flyway, JUnit 5, Testcontainers
- **Frontend**: React 18 + TypeScript (Create React App), TanStack Query, Zustand, Tailwind CSS
- **Real-time**: WebSocket (primary) + SSE fallback; REST event sync
- **API**: `/api/v2` canonical; `/api` v1 `@Deprecated` fallback — see [ADR-003](docs/adr/ADR-003-api-v2-migration.md)
- **Infra**: Docker Compose (local Postgres + Redis), Railway (backend + frontend deploy)

## Repository layout

```
backend/          Java modular monolith — BCE slices under com.example.auction.<feature>
frontend/         CRA app — src/features/<feature>/ + src/shared/
infra/            docker-compose.yml (Postgres 16, Redis 7)
docs/             ADRs, player-data-pipeline guide
data/             Local SoFIFA exports + processed league CSV/SQL (gitignored)
scripts/          Python helpers for card-pool generation (gitignored)
```

Backend feature slices: `lobby`, `session`, `bidding`, `simulation`, `competition`.

Frontend routes: `/lobby`, `/auction/:sessionId`, `/match/:sessionId`, `/tournament/:sessionId`, `/spectate/:sessionId`.

## Quick start

```bash
# Infra
cd infra && docker compose up -d

# Backend (port 8080)
cd backend && mvn spring-boot:run -Dspring-boot.run.profiles=local

# Frontend (port 3000)
cd frontend && npm install && npm start
```

Set `REACT_APP_API_BASE=http://localhost:8080` in `frontend/.env` if needed.

### API version rollback

Default frontend uses v2. To fall back to v1 without backend changes:

```env
REACT_APP_API_VERSION=v1
```

## Card pools

At room creation the host picks a **league** and **minimum overall rating** (70 / 75 / 80). Only cards matching both filters enter the auction pool.

| League (`poolLeague`) | Lobby label | Selectable |
|-----------------------|-------------|------------|
| `PREMIER_LEAGUE` | Premier League | yes |
| `SUPER_LIG` | Trendyol Super Lig | yes |
| `LA_LIGA`, `SERIE_A`, `BUNDESLIGA` | — | coming soon |

A rating threshold is only offered when the league pool has **at least 100 eligible cards** at that level. The lobby UI and `GET /api/v2/lobbies/pool-options` reflect this; room creation is rejected server-side if the pool is too small.

### Adding / refreshing card data

Player cards live in Postgres (`card.pool_league`, `card.rating`, club, nationality, positions). To import from SoFIFA CSV:

1. Put raw export at `data/raw/players.csv`
2. Run `scripts/split_players_by_league.py` → `data/processed/leagues/`
3. Run `scripts/generate_league_cards_sql.py` → SQL for Flyway or manual import

See [docs/player-data-pipeline.md](docs/player-data-pipeline.md) and [data/processed/README.md](data/processed/README.md).

## Deploy

Production runs on **Railway** (separate backend + frontend services). No GitHub Actions CI in this repo — run tests locally before deploy:

```bash
cd backend && mvn verify          # unit + integration (Docker required for IT)
cd frontend && npm test -- --watchAll=false
```

## Architecture

- **Modular monolith** with BCE layout: `{boundary, control, entity}` per feature
- **Command boundary**: `SessionOrchestrator` → `LobbyService` / `AuctionService`; competition via `competition` slice
- **Active session state** in Redis (`ActiveSession`); Postgres for cards, match history, competitions, Flyway migrations
- **Cross-aggregate effects**: Spring `ApplicationEventPublisher` + `@TransactionalEventListener(AFTER_COMMIT)` with idempotent `ProcessedEvent` handlers
- **Auth (MVP)**: opaque UUID session token — REST `Authorization: Bearer`, WS/SSE `?token=` — see [ADR-002](docs/adr/ADR-002-anonymous-player-identity.md)

Key v2 endpoints:

| Area | Path |
|------|------|
| Lobbies | `GET/POST /api/v2/lobbies`, `GET /api/v2/lobbies/pool-options` |
| Session | `GET /api/v2/sessions/{id}`, formation + match-start commands |
| Auction | `POST /api/v2/sessions/{id}/rounds`, bids, pass, ready-next |
| Competition | `GET /api/v2/sessions/{id}/competition`, tournament match open/ack |
| Real-time | `GET /api/v2/sessions/{id}/events/stream` (SSE), WebSocket upgrade |

See [docs/adr/](docs/adr/) for decisions — especially [ADR-005](docs/adr/ADR-005-game-mode-and-competition.md) for tournament/league scope.

## Admin

Session delete endpoints require `X-Admin-Key` when `APP_ADMIN_API_KEY` is set.
