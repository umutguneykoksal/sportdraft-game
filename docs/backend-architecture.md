# SportDraft Backend — Mimari ve Geliştirme Planı

Canlı ürün: [sportdraft.dev](https://www.sportdraft.dev)

---

## 1. Kullandığımız tasarım nedir?

**Modular monolith + feature slice (BCE) + server-authoritative real-time game.**

Bu bir microservice değil; tek deploy edilen Spring Boot uygulaması. Domain, **feature slice**’lara bölünmüş; her slice kendi sorumluluğunda boundary / control / entity katmanlarına sahip.

```
dev.sportdraft.<feature>.{boundary, control, entity}
```

| Slice | Sorumluluk |
|---|---|
| `lobby/` | Oda listesi, create/join, formation, countdown |
| `session/` | Redis session store, disconnect cleanup, arşiv |
| `bidding/` | Auction round’ları, bid, settlement |
| `simulation/` | Rating-weighted maç simülasyonu |
| `competition/` | Turnuva / lig bracket, fixture, replay kuralları |
| `common/` | SSE/WS, auth filter, idempotency, event log |

**Boundary** = HTTP/WS giriş noktası (controller, DTO record’ları).  
**Control** = domain logic + use case’ler (`@Service`, listener, scheduler).  
**Entity** = kalıcı model (JPA entity, Redis aggregate, repository).

Bu yapı DDD’nin tam hali değil; pratikte **“vertical slice + layered boundary”** — küçük ekip için okunabilir, test edilebilir, deploy tek.

---

## 2. Veri ve state modeli

### Aktif oyun (Redis)

Draft + auction sırasında tüm state **tek JSON aggregate**’te:

```
session:{uuid}  →  ActiveSession
  ├── players, status, formations
  ├── rounds[], bids (embedded)
  └── cardPool, scores (in-memory match sonrası)
```

- **Optimistic lock**: `version` alanı + `saveIfVersionMatch`
- **Per-session Redis lock**: `SessionLock.run()` — HTTP + scheduler aynı session’da çakışmasın
- **Index SET/ZSET**: `sessions:active`, `sessions:waiting`, `sessions:status:*`
- TTL: 24 saat (aktif session sürekli yenilenir)

### Kalıcı veri (PostgreSQL)

| Tablo / alan | Ne için |
|---|---|
| `cards` | Kart kataloğu |
| `competition_*` | Turnuva/lig bracket, maçlar, izleme |
| `match_history` | Biten duel replay JSON |
| `processed_event` | Idempotent domain event handler |

> **Not:** Eski JPA `game_session` / `auction_round` / `bid` tabloları V26 migration ile kaldırıldı. **Runtime auction path Redis’tir.**

---

## 3. Real-time ve komut akışı

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

- **Komutlar** REST (`/api/v2`)
- **Canlı güncelleme** WebSocket birincil, SSE yedek
- **Reconnect** ordered event log (`SessionEventLog` ring buffer)
- **Cross-slice decoupling** Spring `@EventListener` + `@Transactional` + `ProcessedEvent` idempotency

---

## 4. Oyun modları

| Mod | Akış |
|---|---|
| **DUEL** (2p) | Lobby → draft → `SessionFinalizedEvent` → sim → skor |
| **TOURNAMENT** (4p) | Shared draft → `DraftCompletedEvent` → semi/final bracket |
| **LEAGUE** (4p) | Shared draft → 3 matchday → tablo → opsiyonel playoff |

Mod farkları `CompetitionModeBehavior` implementasyonlarıyla ayrılır (`LeagueCompetitionMode`, `TournamentCompetitionMode`).

---

## 5. İsimlendirme rehberi (refactor sonrası)

AI/generic isimler temizlendi. Tercih edilen kalıp:

| Eski (kaldırıldı) | Yeni |
|---|---|
| `SessionOrchestrator` | Kaldırıldı — controller → service doğrudan |
| `SessionMutationGuard.mutate()` | `SessionLock.run()` / `runVoid()` |
| `AuctionTurnNormalizer` | `TurnSkipResolver.skipInactiveTurns()` |
| `CompetitionMatchModeHooks` | `CompetitionModeBehavior` |
| `TournamentPhaseCoordinator` | `TournamentBracketFlow` |
| `LeaguePhaseCoordinator` | `LeagueSeasonFlow` |
| `ensureOpenRound()` | `openRoundIfNeeded()` |
| `useAuctionOrchestrator` (FE) | `useSessionSync` + `useAuctionPageState` |

**Domain fiilleri kullan:** `createRoom`, `joinRoom`, `placeBid`, `closeRound`, `archive` — `handle*`, `process*`, `ensure*` sadece gerçekten gerekince.

---

## 6. Test stratejisi

- **Unit**: Mockito, control katmanı
- **Slice**: `@WebMvcTest` controller + exception handler
- **Integration**: `@SpringBootTest` + Testcontainers (Postgres + Redis)
- **E2E**: Playwright (3 mod smoke)
- **Coverage gate**: JaCoCo %85 (control katmanı)

---

## 7. CV’de nasıl anlatılır?

**SportDraft** — multiplayer football card auction draft ([sportdraft.dev](https://www.sportdraft.dev))

- Java 21 / Spring Boot modular monolith (BCE feature slices)
- Server-authoritative game state in Redis; Postgres for competitions and history
- Real-time WebSocket/SSE; optimistic locking + per-session Redis lock
- Domain events with idempotent handlers; Testcontainers + 85% coverage gate
- 3 game modes (duel, tournament, league); deployed on Railway

Yazma: microservices, Kafka, OTEL/Grafana stack, JPA-backed live auction.

---

## 8. Geliştirme planı

### Faz A — Kod tabanı hijyeni (kısa vade)

- [x] AI/generic isim refactor (`SessionLock`, `CompetitionModeBehavior`, …)
- [x] Gereksiz `SessionOrchestrator` kaldırma
- [x] Kullanılmayan JPA auction entity/repo temizliği + Flyway V26 tablo drop
- [x] Yetim Redis index reconciliation (TTL expire edge case)
- [x] `joinLobby` deprecated endpoint kaldırıldı (backend + frontend)

### Faz B — Ürün / auth (orta vade)

- [ ] OAuth / hesap sistemi (anonymous token yerine)
- [ ] Rate limiting (bid, feedback, room create)
- [ ] Tek kullanıcı = tek aktif lobby guard’ı
- [ ] Çoklu replica: scheduler leader election veya Redis tabanlı tick

### Faz C — Kalite ve operasyon (orta vade)

- [ ] Railway log’larda sessionId correlation (MDC genişletme)
- [ ] Health check: Redis + Postgres deep check prod’da
- [ ] Load test: eşzamanlı 4 oda + auction bid latency
- [ ] E2E: tam 44-round draft senaryosu (şu an smoke kısa draft)

### Faz D — Genişleme (uzun vade)

- [ ] Basketbol / ikinci spor modu
- [ ] Ek lig havuzları
- [ ] Spectator iyileştirmeleri
- [ ] Match history arama / profil sayfası

---

## 9. Yerel çalıştırma

```bash
cd infra && docker compose up -d
cd backend && mvn spring-boot:run -Dspring-boot.run.profiles=local
cd frontend && npm start
```

Test:

```bash
cd backend && mvn verify          # Testcontainers — Docker gerekir
cd backend && mvn test -Pskip-it    # unit only
```

---

## 10. Klasör referansı

```
backend/src/main/java/dev/sportdraft/
├── lobby/boundary/          LobbyCommandController, DTOs
├── lobby/control/           LobbyService
├── session/redis/           ActiveSession, ActiveSessionStore, SessionLock
├── session/control/         SessionArchiver, disconnect scheduler
├── bidding/control/         AuctionService, TurnSkipResolver, settlement
├── simulation/control/      MatchSimulator, event listeners
├── competition/control/     LeagueSeasonFlow, TournamentBracketFlow
└── common/                  SSE, WS, web filters, event log
```

Son güncelleme: 2026-05-23
