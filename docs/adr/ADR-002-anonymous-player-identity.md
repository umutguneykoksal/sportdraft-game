# ADR-002: Anonymous Player Identity (No Login)

**Status:** Accepted  
**Date:** 2026-05-20  
**Context:** MVP is a 2-player browser game with no account system. Clients need a stable identity for lobby join, bidding, and SSE auth.

## Decision

Use a **client-generated UUID** stored in `localStorage` as `sessionToken`. It is sent as `Authorization: Bearer <uuid>` on REST and as `?token=<uuid>` on WebSocket/SSE.

This value is **player identity**, not cryptographic authentication:

- The server does not issue or sign tokens.
- Membership is established when the UUID joins a lobby/session (`SessionPlayer.userId`).
- Mutating endpoints verify the bearer UUID is a **member of that session** (e.g. bid, pass, startRound).
- Spectate and health endpoints remain public.

## Consequences

**Pros**

- Zero login friction for portfolio/demo use.
- Simple wire format; works with `EventSource` query-param auth.
- Sufficient for trusted 2-player sessions.

**Cons (known limits)**

- Anyone who learns another player's UUID can impersonate them in that session.
- No ban/account recovery across devices without a future account layer.

## Related hardening (same MVP)

- Admin DELETE routes require `X-Admin-Key` when `APP_ADMIN_API_KEY` is set; hidden (404) when unset.
- Production actuator exposure limited to `health` and `info`.
- Future work: server-issued opaque session tokens bound to lobby join, optional OAuth.

## References

- [CODE_REVIEW.md](../../CODE_REVIEW.md) §1.3
- [frontend/src/shared/session.ts](../../frontend/src/shared/session.ts)
- [backend/.../AuthTokenResolver.java](../../backend/src/main/java/com/example/auction/common/web/AuthTokenResolver.java)
