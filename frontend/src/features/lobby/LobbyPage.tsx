import { useState } from "react";
import { useLobbyFlow } from "./hooks/useLobbyFlow";
import { FORMATION_LABELS, FORMATION_DOTS } from "../../shared/formations";
import type { Formation } from "../../shared/api/contracts";
import { saveNickname } from "../../shared/session";

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
      onClick={onSelect}
      disabled={disabled}
      className={`
        relative rounded-xl overflow-hidden border-2 transition-all duration-200
        ${selected
          ? "border-accent ring-2 ring-accent/40 sm:scale-105"
          : "border-white/10 hover:border-white/30"
        }
        ${disabled ? "opacity-50 cursor-not-allowed" : "cursor-pointer"}
        bg-surface-card w-full flex flex-row sm:flex-col items-stretch
      `}
    >
      {/* Mini pitch with dots -- aspect-ratio keeps proportions without JS */}
      <div
        className="relative shrink-0 w-24 sm:w-full"
        style={{
          aspectRatio: "5 / 7",
          background: "linear-gradient(180deg, #1a5c30 0%, #1e6e3a 50%, #1a5c30 100%)",
        }}
      >
        <div className="absolute left-[10%] right-[10%] top-1/2 border-t border-white/20" />
        <div
          className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-[30%] border border-white/20 rounded-full"
          style={{ aspectRatio: "1 / 1" }}
        />
        {dots.map((dot, i) => (
          <div
            key={i}
            className={`absolute w-2.5 h-2.5 sm:w-3 sm:h-3 rounded-full -translate-x-1/2 -translate-y-1/2 ${
              selected ? "bg-accent shadow-lg shadow-accent/50" : "bg-white/70"
            }`}
            style={{ left: `${dot.x}%`, top: `${dot.y}%` }}
          />
        ))}
      </div>
      <div className={`
        py-2.5 px-4 font-bold text-lg flex items-center justify-center flex-1
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

  const [nicknameInput, setNicknameInput] = useState(session.nickname);
  const isInSession = !!snapshot;
  const status = snapshot?.status;
  const nicknameReady = nicknameInput.trim().length >= 2;

  const onJoin = () => {
    const trimmed = nicknameInput.trim().substring(0, 20);
    saveNickname(trimmed);
    handleJoin();
  };

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

      {/* Nickname + Join */}
      {!isInSession && (
        <div className="w-full max-w-md space-y-4">
          <div className="bg-surface-card rounded-xl border border-white/10 p-5">
            <label className="block text-slate-400 text-sm mb-2">Choose your nickname</label>
            <input
              value={nicknameInput}
              onChange={(e) => setNicknameInput(e.target.value)}
              maxLength={20}
              placeholder="Enter nickname..."
              className="w-full bg-surface-elevated border border-white/10 rounded-lg px-4 py-3 text-white placeholder-slate-500 focus:outline-none focus:ring-2 focus:ring-accent text-lg"
            />
            {nicknameInput.trim().length > 0 && nicknameInput.trim().length < 2 && (
              <p className="text-red-400 text-xs mt-1">Minimum 2 characters</p>
            )}
          </div>
          <button
            onClick={onJoin}
            disabled={loading || !nicknameReady}
            className="w-full bg-accent hover:bg-blue-600 disabled:opacity-50 text-white font-bold py-3 px-8 rounded-xl text-lg transition-colors"
          >
            {loading ? "Joining..." : "Find Match"}
          </button>
        </div>
      )}

      {/* Player info when in session */}
      {isInSession && (
        <div className="bg-surface-card rounded-xl border border-white/10 p-4 mb-6 w-full max-w-md text-center">
          <p className="text-slate-400 text-sm">Playing as</p>
          <p className="text-white font-semibold text-lg">{session.nickname}</p>
        </div>
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
              <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-5 gap-3 mx-auto">
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
