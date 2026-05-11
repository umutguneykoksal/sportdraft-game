import { useLobbyFlow } from "./hooks/useLobbyFlow";
import { TeamPitch } from "../../shared/components/TeamPitch";
import { FORMATION_LABELS, ALL_FORMATIONS } from "../../shared/formations";
import type { Formation } from "../../shared/api/contracts";

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
  return (
    <button
      onClick={onSelect}
      disabled={disabled}
      className={`
        relative rounded-xl overflow-hidden border-2 transition-all duration-200
        ${selected
          ? "border-accent ring-2 ring-accent/40 scale-105"
          : "border-white/10 hover:border-white/30"
        }
        ${disabled ? "opacity-50 cursor-not-allowed" : "cursor-pointer"}
        bg-surface-card
      `}
    >
      <div className="p-2">
        <div className="w-full" style={{ maxWidth: 200 }}>
          <TeamPitch
            formation={formation}
            cards={[]}
            size="small"
            color="#1a5c30"
          />
        </div>
      </div>
      <div className={`
        py-2 px-3 text-center font-bold text-lg
        ${selected ? "bg-accent text-white" : "bg-surface-elevated text-slate-300"}
      `}>
        {FORMATION_LABELS[formation]}
      </div>
    </button>
  );
}

export function LobbyPage() {
  const {
    session,
    snapshot,
    loading,
    error,
    isConnected,
    countdownSecondsLeft,
    formations,
    handleJoin,
    handleLeave,
    chooseFormation,
  } = useLobbyFlow();

  const isInSession = !!snapshot;
  const status = snapshot?.status;

  return (
    <div className="min-h-screen bg-surface flex flex-col items-center px-4 py-8">
      {/* Header */}
      <div className="text-center mb-8">
        <h1 className="text-4xl font-extrabold text-white tracking-tight">
          Football Card Auction
        </h1>
        <p className="text-slate-400 mt-2">
          2-player auction draft game
        </p>
      </div>

      {/* Player Info */}
      <div className="bg-surface-card rounded-xl border border-white/10 p-4 mb-6 w-full max-w-md text-center">
        <p className="text-slate-400 text-sm">Playing as</p>
        <p className="text-white font-semibold text-lg">{session.nickname}</p>
      </div>

      {/* Join / Status */}
      {!isInSession && (
        <button
          onClick={handleJoin}
          disabled={loading}
          className="bg-accent hover:bg-blue-600 disabled:opacity-50 text-white font-bold py-3 px-8 rounded-xl text-lg transition-colors"
        >
          {loading ? "Joining..." : "Find Match"}
        </button>
      )}

      {isInSession && (
        <div className="w-full max-w-2xl space-y-6">
          {/* Status Bar */}
          <div className="bg-surface-card rounded-xl border border-white/10 p-4 flex items-center justify-between">
            <div>
              <span className="text-slate-400 text-sm">Status</span>
              <p className="text-white font-semibold">{status}</p>
            </div>
            <div className="text-center">
              <span className="text-slate-400 text-sm">Players</span>
              <p className="text-white font-semibold">{snapshot.playerCount}/2</p>
            </div>
            <div className="text-right">
              <span className="text-slate-400 text-sm">Formation</span>
              <p className="text-white font-semibold">
                {snapshot.myFormation
                  ? FORMATION_LABELS[snapshot.myFormation]
                  : "—"}
              </p>
            </div>
          </div>

          {/* Countdown */}
          {countdownSecondsLeft !== null && countdownSecondsLeft > 0 && (
            <div className="text-center">
              <div className="inline-flex items-center justify-center w-24 h-24 rounded-full border-4 border-accent bg-surface-card">
                <span className="text-3xl font-bold text-white">{countdownSecondsLeft}</span>
              </div>
              <p className="text-slate-400 mt-2 text-sm">Match starting...</p>
            </div>
          )}

          {/* Waiting for opponent */}
          {status === "WAITING" && (
            <div className="text-center py-8">
              <div className="inline-block animate-pulse">
                <div className="w-3 h-3 bg-accent rounded-full inline-block mx-1"></div>
                <div className="w-3 h-3 bg-accent rounded-full inline-block mx-1 animation-delay-200"></div>
                <div className="w-3 h-3 bg-accent rounded-full inline-block mx-1 animation-delay-400"></div>
              </div>
              <p className="text-slate-400 mt-4">Waiting for an opponent...</p>
            </div>
          )}

          {/* Formation Selection */}
          {status === "FORMATION_SELECT" && (
            <div>
              <h3 className="text-xl font-bold text-white text-center mb-4">
                Choose Your Formation
              </h3>
              <div className="grid grid-cols-3 gap-4">
                {formations.map((f) => (
                  <FormationCard
                    key={f}
                    formation={f}
                    selected={snapshot.myFormation === f}
                    onSelect={() => chooseFormation(f)}
                    disabled={loading}
                  />
                ))}
              </div>
            </div>
          )}

          {/* Leave Button */}
          <div className="text-center">
            <button
              onClick={handleLeave}
              disabled={loading}
              className="text-red-400 hover:text-red-300 text-sm underline disabled:opacity-50 transition-colors"
            >
              Leave Lobby
            </button>
          </div>
        </div>
      )}

      {/* Error */}
      {error && (
        <div className="mt-4 bg-red-900/30 border border-red-500/30 text-red-400 px-4 py-2 rounded-lg">
          {error}
        </div>
      )}

      {/* Connection Warning */}
      {isInSession && !isConnected && (
        <div className="mt-4 bg-amber-900/30 border border-amber-500/30 text-amber-400 px-4 py-2 rounded-lg">
          Reconnecting live updates...
        </div>
      )}
    </div>
  );
}
