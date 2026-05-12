import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { useLobbyFlow } from "./hooks/useLobbyFlow";
import { FORMATION_LABELS, FORMATION_DOTS } from "../../shared/formations";
import type { Formation, LobbyRoomResponse } from "../../shared/api/contracts";
import { SESSION_STATUS_LABELS } from "../../shared/api/contracts";
import { saveNickname } from "../../shared/session";

const STATUS_BADGE_COLORS: Record<string, string> = {
  WAITING: "bg-emerald-500/20 text-emerald-400 border-emerald-500/30",
  COUNTDOWN: "bg-amber-500/20 text-amber-400 border-amber-500/30",
  FORMATION_SELECT: "bg-blue-500/20 text-blue-400 border-blue-500/30",
  AUCTIONING: "bg-purple-500/20 text-purple-400 border-purple-500/30",
  MATCH_START_PENDING: "bg-orange-500/20 text-orange-400 border-orange-500/30",
  FINALIZING: "bg-slate-500/20 text-slate-400 border-slate-500/30",
  AWAITING_RESULT: "bg-slate-500/20 text-slate-400 border-slate-500/30",
};

function StatusBadge({ status }: { status: string }) {
  const colors = STATUS_BADGE_COLORS[status] ?? "bg-slate-500/20 text-slate-400 border-slate-500/30";
  const label = SESSION_STATUS_LABELS[status as keyof typeof SESSION_STATUS_LABELS] ?? status;
  return (
    <span className={`text-xs font-medium px-2.5 py-1 rounded-full border ${colors}`}>
      {label}
    </span>
  );
}

function RoomCard({
  room,
  onJoin,
  onSpectate,
  loading,
}: {
  room: LobbyRoomResponse;
  onJoin: () => void;
  onSpectate: () => void;
  loading: boolean;
}) {
  const canJoin = room.status === "WAITING" && room.playerCount < 2;
  const canSpectate = room.status !== "WAITING" && room.status !== "COUNTDOWN";

  return (
    <div className="bg-surface-card rounded-xl border border-white/10 p-4 flex flex-col sm:flex-row items-start sm:items-center gap-3 transition-all hover:border-white/20">
      <div className="flex-1 min-w-0">
        <div className="flex items-center gap-2 mb-1.5">
          <StatusBadge status={room.status} />
          <span className="text-slate-500 text-xs">
            {room.playerCount}/2 players
            {room.spectatorCount > 0 && ` · ${room.spectatorCount} watching`}
          </span>
        </div>
        <p className="text-white font-medium truncate">
          {room.playerNicknames.length > 0
            ? room.playerNicknames.join(" vs ")
            : "Empty room"}
        </p>
      </div>
      <div className="flex gap-2 shrink-0">
        {canJoin && (
          <button
            onClick={onJoin}
            disabled={loading}
            className="bg-accent hover:bg-blue-600 disabled:opacity-50 text-white font-semibold py-2 px-5 rounded-lg text-sm transition-colors"
          >
            Join
          </button>
        )}
        {canSpectate && (
          <button
            onClick={onSpectate}
            disabled={loading}
            className="bg-slate-700/50 border border-slate-500/30 text-slate-300 hover:bg-slate-700/70 disabled:opacity-50 font-semibold py-2 px-5 rounded-lg text-sm transition-colors"
          >
            Spectate
          </button>
        )}
        {!canJoin && !canSpectate && room.status === "COUNTDOWN" && (
          <span className="text-xs text-slate-500 py-2 px-3">Starting...</span>
        )}
      </div>
    </div>
  );
}

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
  const navigate = useNavigate();
  const {
    session,
    snapshot,
    rooms,
    loading,
    error,
    isConnected,
    countdownSecondsLeft,
    formations,
    handleCreateRoom,
    handleJoinRoom,
    handleLeave,
    handleStartAuction,
    chooseFormation,
  } = useLobbyFlow();

  const [nicknameInput, setNicknameInput] = useState(session.nickname);
  const isInSession = snapshot?.joined === true;
  const status = snapshot?.status;
  const nicknameReady = nicknameInput.trim().length >= 2;

  const onCreateRoom = () => {
    const trimmed = nicknameInput.trim().substring(0, 20);
    saveNickname(trimmed);
    handleCreateRoom();
  };

  const onJoinRoom = (roomSessionId: string) => {
    const trimmed = nicknameInput.trim().substring(0, 20);
    saveNickname(trimmed);
    handleJoinRoom(roomSessionId);
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

      {/* Not in a session: show nickname + create room + room list */}
      {!isInSession && (
        <div className="w-full max-w-2xl space-y-6">
          {/* Nickname + Create Room */}
          <div className="bg-surface-card rounded-xl border border-white/10 p-5">
            <label className="block text-slate-400 text-sm mb-2">Choose your nickname</label>
            <div className="flex gap-3">
              <input
                value={nicknameInput}
                onChange={(e) => setNicknameInput(e.target.value)}
                maxLength={20}
                placeholder="Enter nickname..."
                className="flex-1 bg-surface-elevated border border-white/10 rounded-lg px-4 py-3 text-white placeholder-slate-500 focus:outline-none focus:ring-2 focus:ring-accent text-lg"
              />
              <button
                onClick={onCreateRoom}
                disabled={loading || !nicknameReady}
                className="bg-accent hover:bg-blue-600 disabled:opacity-50 text-white font-bold py-3 px-6 rounded-xl text-lg transition-colors whitespace-nowrap"
              >
                {loading ? "Creating..." : "Create Room"}
              </button>
            </div>
            {nicknameInput.trim().length > 0 && nicknameInput.trim().length < 2 && (
              <p className="text-red-400 text-xs mt-1">Minimum 2 characters</p>
            )}
          </div>

          {/* Room List */}
          <div>
            <div className="flex items-center justify-between mb-3">
              <h2 className="text-lg font-semibold text-white">Active Rooms</h2>
              <span className="text-slate-500 text-xs">Auto-refreshes every 3s</span>
            </div>
            {rooms.length === 0 ? (
              <div className="bg-surface-card rounded-xl border border-white/10 p-8 text-center">
                <p className="text-slate-500">No active rooms. Create one to get started!</p>
              </div>
            ) : (
              <div className="space-y-3">
                {rooms.map((room) => (
                  <RoomCard
                    key={room.sessionId}
                    room={room}
                    onJoin={() => onJoinRoom(room.sessionId)}
                    onSpectate={() => navigate(`/spectate/${room.sessionId}`)}
                    loading={loading || !nicknameReady}
                  />
                ))}
              </div>
            )}
          </div>
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
              <p className="text-white font-semibold">{status ? SESSION_STATUS_LABELS[status] : "—"}</p>
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

          {/* Lobby Countdown */}
          {status === "COUNTDOWN" && countdownSecondsLeft !== null && countdownSecondsLeft > 0 && (
            <div className="text-center">
              <div className="inline-flex items-center justify-center w-24 h-24 rounded-full border-4 border-accent bg-surface-card">
                <span className="text-3xl font-bold text-white">{countdownSecondsLeft}</span>
              </div>
              <p className="text-slate-400 mt-2 text-sm">Opponent found! Starting...</p>
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
              <p className="text-slate-400 mt-4">Waiting for an opponent to join...</p>
            </div>
          )}

          {/* Formation Selection */}
          {status === "FORMATION_SELECT" && (
            <div className="space-y-6">
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
                      disabled={loading || snapshot.matchStartConfirmedByMe}
                    />
                  ))}
                </div>
              </div>

              {/* Formation Ready Countdown */}
              {countdownSecondsLeft !== null && countdownSecondsLeft > 0 && snapshot.matchStartReadyCount > 0 && (
                <div className="text-center">
                  <div className="inline-flex items-center justify-center w-20 h-20 rounded-full border-4 border-accent bg-surface-card">
                    <span className="text-2xl font-bold text-white">{countdownSecondsLeft}</span>
                  </div>
                  <p className="text-slate-400 mt-2 text-sm">
                    {snapshot.matchStartConfirmedByMe
                      ? "Waiting for opponent..."
                      : "Opponent is ready! Auction starting..."}
                  </p>
                </div>
              )}

              {/* Start + Leave buttons */}
              <div className="flex gap-3 justify-center">
                <button
                  onClick={handleStartAuction}
                  disabled={loading || snapshot.matchStartConfirmedByMe}
                  className={`font-bold py-3 px-8 rounded-xl text-lg transition-colors ${
                    snapshot.matchStartConfirmedByMe
                      ? "bg-accent-green/20 text-accent-green border border-accent-green/30 cursor-default"
                      : "bg-accent hover:bg-blue-600 disabled:opacity-50 text-white"
                  }`}
                >
                  {snapshot.matchStartConfirmedByMe
                    ? `Ready (${snapshot.matchStartReadyCount}/2)`
                    : "Start Auction"}
                </button>
                <button
                  onClick={handleLeave}
                  disabled={loading}
                  className="bg-red-900/30 border border-red-500/30 text-red-400 hover:bg-red-900/50 font-bold py-3 px-8 rounded-xl text-lg transition-colors disabled:opacity-50"
                >
                  Leave
                </button>
              </div>
            </div>
          )}

          {/* Leave Button (non-formation phases) */}
          {status !== "FORMATION_SELECT" && (
            <div className="text-center">
              <button
                onClick={handleLeave}
                disabled={loading}
                className="text-red-400 hover:text-red-300 text-sm underline disabled:opacity-50 transition-colors"
              >
                Leave Room
              </button>
            </div>
          )}
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
