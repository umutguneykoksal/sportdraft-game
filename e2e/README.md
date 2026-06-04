# E2E (Playwright)

Smoke tests for **Duel**, **Tournament**, and **League**: lobby join → formation → auction page entry. They do **not** run a full 44-round draft.

## Prerequisites

- A running stack (local or remote)
- Node 20+

## Local run

1. Start infra and backend (see root [README](../README.md)).
2. Start the frontend (`cd frontend && npm start` → usually port 3000).
3. From this folder:

```bash
npm ci
set E2E_BASE_URL=http://localhost:3000   # Windows CMD
# export E2E_BASE_URL=http://localhost:3000   # macOS/Linux
npm test
```

If `E2E_BASE_URL` is unset, tests are skipped (same as CI when not configured).

## CI

GitHub Actions sets `E2E_BASE_URL` to the deployed or staging app URL and runs `npm test` with one worker.

## Layout

- `tests/duel-smoke.spec.ts`
- `tests/tournament-smoke.spec.ts`
- `tests/league-smoke.spec.ts`
- `tests/helpers/` — shared lobby/flow helpers

Reports: `playwright-report/` and `test-results/` (gitignored).
