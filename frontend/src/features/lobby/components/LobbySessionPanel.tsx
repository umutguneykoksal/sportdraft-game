import type { Formation, GameMode, SessionSnapshotResponse } from "../../../shared/api/contracts";
import { FORMATION_LABELS, FORMATION_DOTS } from "../../../shared/formations";
import { GAME_MODE_LABELS, SESSION_STATUS_LABELS, poolLeagueLabel } from "../../../shared/api/contracts";
import {
  FORMATION_ALL_READY_COUNTDOWN_SECONDS,
  LOBBY_PAIRING_COUNTDOWN_SECONDS,
  inferCountdownPhase,
} from "../../../shared/lobbyTiming";
import { cn } from "../../../shared/utils/cn";

function FormationCard({
  formation,
  selected,
  onSelect,
  disabled,
}: {
  formation: Formation;
  selected: boolean;
  onSelect: () => void;
  disabled: boolean;
}) {
  const dots = FORMATION_DOTS[formation];

  return (
    <button
      type="button"
      data-testid={`lobby-formation-${formation}`}
      onClick={onSelect}
      disabled={disabled}
      className={cn(
        "relative flex w-full touch-manipulation flex-col overflow-hidden rounded-md border bg-surface-card transition-colors",
        "min-h-[88px] sm:min-h-[44px] sm:h-full",
        selected ? "border-accent ring-1 ring-accent/40" : "border-white/10 hover:border-white/25 active:border-white/30",
        disabled ? "cursor-not-allowed opacity-50" : "cursor-pointer"
      )}
    >
      <div
        className="relative min-h-[60px] w-full flex-1 sm:min-h-0"
        style={{
          background: "linear-gradient(180deg, #1a5c30 0%, #1e6e3a 50%, #1a5c30 100%)",
        }}
      >
        <div className="absolute left-[10%] right-[10%] top-1/2 border-t border-white/20" />
        <div className="absolute top-1/2 left-1/2 h-[28%] w-[28%] -translate-x-1/2 -translate-y-1/2 rounded-full border border-white/20" />
        {dots.map((dot, i) => (
          <div
            key={i}
            className={cn(
              "absolute h-2.5 w-2.5 -translate-x-1/2 -translate-y-1/2 rounded-full sm:h-2.5 sm:w-2.5",
              selected ? "bg-accent shadow-sm shadow-accent/50" : "bg-white/80"
            )}
            style={{ left: `${dot.x}%`, top: `${dot.y}%` }}
          />
        ))}
      </div>
      <div
        className={cn(
          "shrink-0 py-1.5 text-center text-[11px] font-bold leading-none sm:text-[11px]",
          selected ? "bg-accent text-white" : "bg-surface-elevated text-slate-300"
        )}
      >
        {FORMATION_LABELS[formation]}
      </div>
    </button>
  );
}

function CountdownRing({
  seconds,
  label,
  testId,
}: {
  seconds: number;
  label: string;
  testId?: string;
}) {
  return (
    <div className="flex flex-col items-center py-6 text-center" data-testid={testId}>
      <div className="inline-flex h-20 w-20 items-center justify-center rounded-full border-4 border-accent bg-surface-card">
        <span className="text-3xl font-bold tabular-nums text-white">{seconds}</span>
      </div>
      <p className="mt-3 max-w-xs text-sm leading-relaxed text-slate-400">{label}</p>
    </div>
  );
}

function FormationActionBar({
  snapshot,
  maxPlayers,
  formationPickActive,
  showReadyControl,
  loading,
  onStartAuction,
  onLeave,
}: {
  snapshot: SessionSnapshotResponse;
  maxPlayers: number;
  formationPickActive: boolean;
  showReadyControl: boolean;
  loading: boolean;
  onStartAuction: () => void;
  onLeave: () => void;
}) {
  const leaveOnly = !showReadyControl;

  return (
    <div className={cn("flex gap-3", leaveOnly && "flex-col")}>
      {showReadyControl && (
        <button
          data-testid="lobby-confirm-formation-ready"
          onClick={onStartAuction}
          disabled={loading || snapshot.matchStartConfirmedByMe || !snapshot.myFormation}
          className={cn(
            "min-h-[44px] min-w-0 flex-1 rounded-lg px-4 py-2.5 text-sm font-bold transition-colors",
            snapshot.matchStartConfirmedByMe
              ? "cursor-default whitespace-nowrap border border-accent-green/40 bg-accent-green/15 text-accent-green"
              : "bg-accent text-white hover:bg-blue-600 disabled:opacity-50"
          )}
        >
          {snapshot.matchStartConfirmedByMe
            ? `Ready (${snapshot.matchStartReadyCount}/${maxPlayers})`
            : "Start Auction"}
        </button>
      )}
      <button
        type="button"
        data-testid="lobby-leave"
        onClick={onLeave}
        disabled={loading}
        className={cn(
          "min-h-[44px] rounded-lg border border-red-500/30 bg-red-900/30 px-5 py-2.5 text-sm font-bold text-red-400 hover:bg-red-900/50 disabled:opacity-50",
          leaveOnly ? "w-full" : "shrink-0 basis-auto min-w-[5.5rem]"
        )}
      >
        Leave
      </button>
    </div>
  );
}

export interface LobbySessionPanelProps {
  nickname: string;
  snapshot: SessionSnapshotResponse;
  maxPlayers: number;
  gameMode: GameMode;
  countdownSecondsLeft: number | null;
  formations: Formation[];
  loading: boolean;
  onChooseFormation: (formation: Formation) => void;
  onStartAuction: () => void;
  onLeave: () => void;
}

export function LobbySessionPanel({
  nickname,
  snapshot,
  maxPlayers,
  gameMode,
  countdownSecondsLeft,
  formations,
  loading,
  onChooseFormation,
  onStartAuction,
  onLeave,
}: LobbySessionPanelProps) {
  const status = snapshot.status;
  const countdownPhase = inferCountdownPhase(snapshot);
  const pairingCountdownActive =
    countdownPhase === "PAIRING"
    && countdownSecondsLeft != null
    && countdownSecondsLeft > 0;
  const formationStartCountdownActive =
    countdownPhase === "FORMATION_AUCTION_START"
    && countdownSecondsLeft != null
    && countdownSecondsLeft > 0;
  /** Until this client confirms ready, show the formation grid. */
  const formationPickActive =
    status === "FORMATION_SELECT" && !snapshot.matchStartConfirmedByMe;
  const showReadyControl =
    status === "FORMATION_SELECT"
    && (formationPickActive
      || (snapshot.matchStartConfirmedByMe
        && snapshot.matchStartReadyCount < maxPlayers
        && !formationStartCountdownActive));

  const waitingHint =
    formationPickActive
    && snapshot.matchStartReadyCount >= 1
    && snapshot.matchStartReadyCount < maxPlayers
      ? "Opponent is ready — pick a formation and start"
      : null;

  const poolLabel =
    (snapshot.minPoolRating ?? 0) > 0
      ? `${poolLeagueLabel(snapshot.poolLeague)} · ${snapshot.minPoolRating}+ OVR`
      : null;

  const pairingCountdownLabel = `Opponent found — formation pick starts in ${LOBBY_PAIRING_COUNTDOWN_SECONDS}s`;
  const formationCountdownLabel = `Auction starts in ${FORMATION_ALL_READY_COUNTDOWN_SECONDS}s`;

  return (
    <div
      className={cn(
        "flex h-full min-h-0 w-full min-w-0 flex-col overflow-hidden rounded-xl border border-white/10 bg-surface-card/95 shadow-2xl shadow-black/30 backdrop-blur-sm sm:rounded-2xl",
        formationPickActive &&
          "max-sm:overflow-y-auto max-sm:overscroll-y-contain max-sm:[-webkit-overflow-scrolling:touch]"
      )}
    >
      <header
        className={cn(
          "shrink-0 border-b border-white/10 bg-surface-card/95 px-4 py-3 sm:px-5",
          formationPickActive && "max-sm:sticky max-sm:top-0 max-sm:z-10 max-sm:backdrop-blur-sm"
        )}
      >
        <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between sm:gap-3">
          <div className="min-w-0 flex-1">
            <p className="truncate text-base font-bold text-white">{nickname}</p>
            <p className="text-[11px] text-slate-400">
              {status ? SESSION_STATUS_LABELS[status] : "—"}
              {" · "}
              {snapshot.playerCount}/{maxPlayers}
              {gameMode !== "DUEL" ? ` · ${GAME_MODE_LABELS[gameMode]}` : ""}
            </p>
            {poolLabel && (
              <p className="truncate text-[11px] text-slate-500">{poolLabel}</p>
            )}
          </div>
          <div className="flex shrink-0 items-center justify-between gap-3 sm:block sm:text-right">
            <p className="text-[10px] uppercase tracking-wide text-slate-500">Formation</p>
            <p className="text-sm font-semibold text-white">
              {snapshot.myFormation ? FORMATION_LABELS[snapshot.myFormation] : "—"}
            </p>
          </div>
        </div>
      </header>

      <div className="flex min-h-0 flex-1 flex-col overflow-hidden">
        <main
          className={cn(
            "min-h-0 flex-1",
            formationPickActive
              ? "max-sm:flex-none sm:flex sm:flex-col sm:overflow-hidden"
              : "overflow-y-auto overscroll-y-contain [-webkit-overflow-scrolling:touch]"
          )}
        >
          {pairingCountdownActive && countdownSecondsLeft != null && (
            <div className="flex shrink-0 flex-col items-center justify-center px-4 py-4 sm:flex-1">
              <p className="mb-2 text-[10px] font-semibold uppercase tracking-wide text-amber-400/90">
                Pairing · {LOBBY_PAIRING_COUNTDOWN_SECONDS}s
              </p>
              <CountdownRing
                seconds={countdownSecondsLeft}
                label={pairingCountdownLabel}
                testId="lobby-match-countdown"
              />
            </div>
          )}

          {status === "WAITING" && (
            <div className="flex shrink-0 flex-col items-center justify-center px-4 py-8 text-center sm:flex-1">
              <div className="inline-block animate-pulse">
                <div className="mx-1 inline-block h-2.5 w-2.5 rounded-full bg-accent" />
                <div className="mx-1 inline-block h-2.5 w-2.5 rounded-full bg-accent animation-delay-200" />
                <div className="mx-1 inline-block h-2.5 w-2.5 rounded-full bg-accent animation-delay-400" />
              </div>
              <p className="mt-4 text-sm text-slate-400">
                Waiting for {maxPlayers - snapshot.playerCount} more player
                {maxPlayers - snapshot.playerCount === 1 ? "" : "s"}...
              </p>
            </div>
          )}

          {status === "FORMATION_SELECT"
            && snapshot.matchStartConfirmedByMe
            && !formationStartCountdownActive
            && snapshot.matchStartReadyCount < maxPlayers && (
            <div className="flex shrink-0 flex-col items-center justify-center px-4 py-6 text-center sm:flex-1">
              <p className="text-sm text-slate-400">Waiting for opponent to confirm...</p>
              {snapshot.myFormation && (
                <p className="mt-2 text-xs text-slate-500">
                  Locked in {FORMATION_LABELS[snapshot.myFormation]}
                </p>
              )}
            </div>
          )}

          {formationStartCountdownActive && countdownSecondsLeft != null && (
            <div className="flex shrink-0 flex-col items-center justify-center px-4 py-4 sm:flex-1">
              <p className="mb-2 text-[10px] font-semibold uppercase tracking-wide text-accent/90">
                Auction start · {FORMATION_ALL_READY_COUNTDOWN_SECONDS}s
              </p>
              <CountdownRing
                seconds={countdownSecondsLeft}
                label={formationCountdownLabel}
                testId="lobby-auction-countdown"
              />
              {snapshot.myFormation && (
                <p className="text-center text-xs text-slate-500">
                  Locked in {FORMATION_LABELS[snapshot.myFormation]}
                </p>
              )}
            </div>
          )}

          {formationPickActive && (
            <div className="flex flex-col px-4 pt-3 pb-4 sm:min-h-0 sm:flex-1 sm:px-5 sm:pb-2">
              <p className="mb-3 shrink-0 text-center text-sm font-semibold text-slate-300">
                Choose your formation
              </p>
              <p className="mb-3 shrink-0 text-center text-[11px] text-slate-500 sm:hidden">
                Scroll for all formations — Start Auction stays at the bottom
              </p>
              <div className="grid grid-cols-2 gap-2.5 sm:min-h-0 sm:flex-1 sm:grid-cols-5 sm:grid-rows-2 sm:gap-2">
                {formations.map((f) => (
                  <FormationCard
                    key={f}
                    formation={f}
                    selected={snapshot.myFormation === f}
                    onSelect={() => onChooseFormation(f)}
                    disabled={loading || snapshot.matchStartConfirmedByMe}
                  />
                ))}
              </div>
              <div className="h-28 shrink-0 sm:hidden" aria-hidden />
            </div>
          )}

          {status !== "FORMATION_SELECT" && status !== "WAITING" && status !== "COUNTDOWN" && (
            <div className="flex shrink-0 items-center justify-center px-4 py-8 text-sm text-slate-400 sm:flex-1">
              {status ? SESSION_STATUS_LABELS[status] : "Loading session..."}
            </div>
          )}
        </main>

        {status === "FORMATION_SELECT" && (
          <footer
            className={cn(
              "shrink-0 border-t border-white/10 bg-surface-elevated/95 px-4 py-3 sm:px-5",
              "pb-[max(0.75rem,env(safe-area-inset-bottom))]",
              formationPickActive &&
                "max-sm:sticky max-sm:bottom-0 max-sm:z-10 max-sm:backdrop-blur-sm"
            )}
          >
            {waitingHint && (
              <p className="mb-3 text-center text-xs leading-relaxed text-slate-400">
                {waitingHint}
              </p>
            )}
            <FormationActionBar
              snapshot={snapshot}
              maxPlayers={maxPlayers}
              formationPickActive={formationPickActive}
              showReadyControl={showReadyControl}
              loading={loading}
              onStartAuction={onStartAuction}
              onLeave={onLeave}
            />
          </footer>
        )}

        {status !== "FORMATION_SELECT" && (
          <footer className="shrink-0 border-t border-white/10 px-4 py-2.5 text-center sm:py-2.5">
            <button
              onClick={onLeave}
              disabled={loading}
              className="min-h-[44px] text-xs text-red-400 underline transition-colors hover:text-red-300 disabled:opacity-50"
            >
              Leave room
            </button>
          </footer>
        )}
      </div>
    </div>
  );
}
