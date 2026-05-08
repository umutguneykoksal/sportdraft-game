# Football Card Auction Game

Browser-based 2-player football card auction draft game.

## Stack

- **Backend**: Java 21, Spring Boot 3.x, PostgreSQL, Redis
- **Frontend**: React 18 + TypeScript (Create React App), TanStack Query, Zustand
- **Real-time**: REST + SSE (`EventSource`)
- **Infra**: Docker Compose (local), Railway (backend + frontend)

## Current Status

Step 2 scaffold is in place:

- `backend/` Spring Boot app skeleton (`pom.xml`, profiles, Flyway init migration)
- `frontend/` CRA + TypeScript skeleton (`react-scripts`, router shell)
- `infra/docker-compose.yml` for local Postgres + Redis

## Next

- Implement lobby flow (join, 5-second cancel window, formation selection)
- Implement auction loop + hard positional constraint
- Implement simulation + result screen
