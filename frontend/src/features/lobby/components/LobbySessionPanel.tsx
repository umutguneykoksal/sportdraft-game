import type { Formation, GameMode, SessionSnapshotResponse } from "../../../shared/api/contracts";
import { FORMATION_LABELS, FORMATION_DOTS } from "../../../shared/formations";
import { GAME_MODE_LABELS, SESSION_STATUS_LABELS, poolLeagueLabel } from "../../../shared/api/contracts";
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
        "relative flex w-full flex-col overflow-hidden rounded-md border bg-surface-card transition-colors",
        selected ? "border-accent ring-1 ring-accent/40" : "border-white/10 hover:border-white/25",
        disabled ? "cursor-not-allowed opacity-50" : "cursor-pointer"
      )}
    >
      <div
        className="relative w-full aspect-[5/6]"
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
              "absolute h-2 w-2 -translate-x-1/2 -translate-y-1/2 rounded-full sm:h-2.5 sm:w-2.5",
              selected ? "bg-accent shadow-sm shadow-accent/50" : "bg-white/80"
            )}
            style={{ left: `${dot.x}%`, top: `${dot.y}%` }}
          />
        ))}
      </div>
      <div
        className={cn(
          "shrink-0 py-1 text-center text-[10px] font-bold leading-none sm:text-[11px]",
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
        <span className="text-3xl font-bold text-white">{seconds}</span>
      </div>
      <p className="mt-3 text-sm text-slate-400">{label}</p>
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
  const allPlayersReady = snapshot.matchStartReadyCount >= maxPlayers;
  const auctionCountdownActive =
    status === "FORMATION_SELECT"
    && allPlayersReady
    && countdownSecondsLeft != null
    && countdownSecondsLeft > 0;
  const lobbyCountdownActive =
    status === "COUNTDOWN"
    && countdownSecondsLeft != null
    && countdownSecondsLeft > 0;
  const formationPickActive = status === "FORMATION_SELECT" && !auctionCountdownActive;

  const waitingHint =
    formationPickActive
    && snapshot.matchStartReadyCount >= 1
    && snapshot.matchStartReadyCount < maxPlayers
      ? snapshot.matchStartConfirmedByMe
        ? "Waiting for opponent to confirm..."
        : "Opponent is ready — pick a formation and start"
      : null;

  const poolLabel =
    (snapshot.minPoolRating ?? 0) > 0
      ? `${poolLeagueLabel(snapshot.poolLeague)} · ${snapshot.minPoolRating}+ OVR`
      : null;

  return (
    <div className="flex h-full min-h-0 w-full min-w-0 flex-col overflow-hidden rounded-xl border border-white/10 bg-surface-card/95 shadow-2xl shadow-black/30 sm:rounded-2xl">
      <header className="flex shrink-0 items-center justify-between gap-3 border-b border-white/10 px-5 py-3">
        <div className="min-w-0">
          <p className="truncate text-base font-bold text-white">{nickname}</p>
          <p className="truncate text-[11px] text-slate-400">
            {status ? SESSION_STATUS_LABELS[status] : "—"}
            {" · "}
            {snapshot.playerCount}/{maxPlayers}
            {gameMode !== "DUEL" ? ` · ${GAME_MODE_LABELS[gameMode]}` : ""}
            {poolLabel ? ` · ${poolLabel}` : ""}
          </p>
        </div>
        <div className="shrink-0 text-right">
          <p className="text-[10px] uppercase tracking-wide text-slate-500">Formation</p>
          <p className="text-sm font-semibold text-white">
            {snapshot.myFormation ? FORMATION_LABELS[snapshot.myFormation] : "—"}
          </p>
        </div>
      </header>

      <div className="flex min-h-0 flex-1 flex-col overflow-hidden">
        {lobbyCountdownActive && countdownSecondsLeft != null && (
          <div className="flex flex-1 items-center justify-center px-4">
            <CountdownRing
              seconds={countdownSecondsLeft}
              label="Opponent found! Starting..."
              testId="lobby-match-countdown"
            />
          </div>
        )}

        {status === "WAITING" && (
          <div className="flex flex-1 flex-col items-center justify-center px-4 text-center">
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

        {status === "FORMATION_SELECT" && auctionCountdownActive && countdownSecondsLeft != null && (
          <div className="flex flex-1 flex-col items-center justify-center px-4">
            <CountdownRing
              seconds={countdownSecondsLeft}
              label="Auction starting..."
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
          <div className="flex flex-1 flex-col justify-center px-4 pb-4 pt-4 sm:px-5">
            <p className="mb-3 text-center text-sm font-semibold text-slate-300">
              Choose your formation
            </p>
            <div className="grid grid-cols-5 gap-2 sm:gap-2.5">
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
          </div>
        )}

        {status === "FORMATION_SELECT" && (
          <footer className="shrink-0 border-t border-white/10 bg-surface-elevated/20 px-5 py-4">
            {waitingHint && (
              <p className="mb-3 text-center text-xs leading-relaxed text-slate-400">
                {waitingHint}
              </p>
            )}
            <div className="flex gap-3">
              {formationPickActive && (
                <button
                  data-testid="lobby-confirm-formation-ready"
                  onClick={onStartAuction}
                  disabled={loading || snapshot.matchStartConfirmedByMe || !snapshot.myFormation}
                  className={cn(
                    "flex-1 rounded-lg py-2.5 text-sm font-bold transition-colors",
                    snapshot.matchStartConfirmedByMe
                      ? "cursor-default border border-accent-green/30 bg-accent-green/20 text-accent-green"
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
                  "rounded-lg border border-red-500/30 bg-red-900/30 px-5 py-2.5 text-sm font-bold text-red-400 hover:bg-red-900/50 disabled:opacity-50",
                  formationPickActive ? "" : "w-full"
                )}
              >
                Leave
              </button>
            </div>
          </footer>
        )}

        {status !== "FORMATION_SELECT" && status !== "WAITING" && status !== "COUNTDOWN" && (
          <div className="flex flex-1 items-center justify-center px-4 text-sm text-slate-400">
            {status ? SESSION_STATUS_LABELS[status] : "Loading session..."}
          </div>
        )}

        {status !== "FORMATION_SELECT" && (
          <footer className="shrink-0 border-t border-white/10 px-4 py-2.5 text-center">
            <button
              onClick={onLeave}
              disabled={loading}
              className="text-xs text-red-400 underline transition-colors hover:text-red-300 disabled:opacity-50"
            >
              Leave room
            </button>
          </footer>
        )}
      </div>
    </div>
  );
}
