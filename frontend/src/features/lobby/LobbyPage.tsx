import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { toast } from "sonner";
import { useLobbyFlow } from "./hooks/useLobbyFlow";
import { LobbyEntryPanel } from "./components/LobbyEntryPanel";
import { FORMATION_LABELS, FORMATION_DOTS } from "../../shared/formations";
import type { Formation } from "../../shared/api/contracts";
import { GAME_MODE_LABELS, SESSION_STATUS_LABELS, poolLeagueLabel } from "../../shared/api/contracts";
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
      data-testid={`lobby-formation-${formation}`}
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
    showReconnecting,
    countdownSecondsLeft,
    formations,
    handleCreateRoom,
    handleJoinRoom,
    handleLeave,
    handleStartAuction,
    chooseFormation,
    createGameMode,
    setCreateGameMode,
    createMinPoolRating,
    setCreateMinPoolRating,
    createPoolLeague,
    setCreatePoolLeague,
    poolMinRatingOptions,
    poolMinRatingOptionsLoaded,
  } = useLobbyFlow();

  const maxPlayers = snapshot?.maxPlayers ?? 2;
  const gameMode = snapshot?.gameMode ?? "DUEL";

  const [nicknameInput, setNicknameInput] = useState(session.nickname);
  const isInSession = snapshot?.joined === true;
  const status = snapshot?.status;
  const nicknameReady = nicknameInput.trim().length >= 2;
  const hasSelectablePoolRating = poolMinRatingOptions.some((o) => o.selectable);
  const canCreateRoom = nicknameReady
    && (!poolMinRatingOptionsLoaded || hasSelectablePoolRating);

  useEffect(() => {
    if (error) {
      toast.error(error);
    }
  }, [error]);

  useEffect(() => {
    if (showReconnecting) {
      toast.loading("Reconnecting live updates...", { id: "lobby-reconnect" });
      return;
    }
    toast.dismiss("lobby-reconnect");
  }, [showReconnecting]);

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
    <div className="lobby-bg flex h-dvh items-center justify-center overflow-hidden p-3 sm:p-4">
      {!isInSession && (
        <div className="h-full max-h-[min(560px,calc(100dvh-1.5rem))] w-full max-w-5xl">
          <LobbyEntryPanel
            nicknameInput={nicknameInput}
            onNicknameChange={setNicknameInput}
            nicknameReady={nicknameReady}
            createGameMode={createGameMode}
            onCreateGameModeChange={setCreateGameMode}
            createPoolLeague={createPoolLeague}
            onCreatePoolLeagueChange={setCreatePoolLeague}
            createMinPoolRating={createMinPoolRating}
            onCreateMinPoolRatingChange={setCreateMinPoolRating}
            poolMinRatingOptions={poolMinRatingOptions}
            poolMinRatingOptionsLoaded={poolMinRatingOptionsLoaded}
            hasSelectablePoolRating={hasSelectablePoolRating}
            canCreateRoom={canCreateRoom}
            loading={loading}
            rooms={rooms}
            onCreateRoom={onCreateRoom}
            onJoinRoom={onJoinRoom}
            onSpectateRoom={(sessionId) => navigate(`/spectate/${sessionId}`)}
          />
        </div>
      )}

      {isInSession && (
        <div className="flex h-full w-full max-w-2xl flex-col overflow-y-auto py-2">
          <div className="bg-surface-card rounded-xl border border-white/10 p-4 mb-6 w-full text-center">
            <p className="text-slate-400 text-sm">Playing as</p>
            <p className="text-white font-semibold text-lg">{session.nickname}</p>
          </div>

          <div className="w-full space-y-6">
          <div className="bg-surface-card rounded-xl border border-white/10 p-4 flex flex-wrap items-center justify-between gap-4">
            <div>
              <span className="text-slate-400 text-sm">Status</span>
              <p className="text-white font-semibold">{status ? SESSION_STATUS_LABELS[status] : "—"}</p>
            </div>
            <div className="text-center">
              <span className="text-slate-400 text-sm">Players</span>
              <p className="text-white font-semibold">
                {snapshot.playerCount}/{maxPlayers}
                {gameMode !== "DUEL" && (
                  <span className="text-slate-500 text-xs ml-1">({GAME_MODE_LABELS[gameMode]})</span>
                )}
              </p>
            </div>
            {(snapshot.minPoolRating ?? 0) > 0 && (
              <div className="text-center">
                <span className="text-slate-400 text-sm">Pool</span>
                <p className="text-white font-semibold">
                  {poolLeagueLabel(snapshot.poolLeague)} · {snapshot.minPoolRating}+ OVR
                </p>
              </div>
            )}
            <div className="text-right">
              <span className="text-slate-400 text-sm">Formation</span>
              <p className="text-white font-semibold">
                {snapshot.myFormation
                  ? FORMATION_LABELS[snapshot.myFormation]
                  : "—"}
              </p>
            </div>
          </div>

          {status === "COUNTDOWN" && countdownSecondsLeft !== null && countdownSecondsLeft > 0 && (
            <div className="text-center">
              <div className="inline-flex items-center justify-center w-24 h-24 rounded-full border-4 border-accent bg-surface-card">
                <span className="text-3xl font-bold text-white">{countdownSecondsLeft}</span>
              </div>
              <p className="text-slate-400 mt-2 text-sm">Opponent found! Starting...</p>
            </div>
          )}

          {status === "WAITING" && (
            <div className="text-center py-8">
              <div className="inline-block animate-pulse">
                <div className="w-3 h-3 bg-accent rounded-full inline-block mx-1"></div>
                <div className="w-3 h-3 bg-accent rounded-full inline-block mx-1 animation-delay-200"></div>
                <div className="w-3 h-3 bg-accent rounded-full inline-block mx-1 animation-delay-400"></div>
              </div>
              <p className="text-slate-400 mt-4">
                Waiting for {maxPlayers - snapshot.playerCount} more player
                {maxPlayers - snapshot.playerCount === 1 ? "" : "s"}...
              </p>
            </div>
          )}

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

              {snapshot.matchStartReadyCount >= 1
                && snapshot.matchStartReadyCount < maxPlayers && (
                <p className="text-center text-slate-400 text-sm">
                  {snapshot.matchStartConfirmedByMe
                    ? "Waiting for opponent to confirm..."
                    : "Opponent is ready! Select formation and click Start Auction."}
                </p>
              )}

              {snapshot.matchStartReadyCount === maxPlayers
                && countdownSecondsLeft !== null
                && countdownSecondsLeft > 0 && (
                <div className="text-center">
                  <div className="inline-flex items-center justify-center w-20 h-20 rounded-full border-4 border-accent bg-surface-card">
                    <span className="text-2xl font-bold text-white">{countdownSecondsLeft}</span>
                  </div>
                  <p className="text-slate-400 mt-2 text-sm">Auction starting...</p>
                </div>
              )}

              <div className="flex gap-3 justify-center">
                <button
                  data-testid="lobby-confirm-formation-ready"
                  onClick={handleStartAuction}
                  disabled={loading || snapshot.matchStartConfirmedByMe}
                  className={`font-bold py-3 px-8 rounded-xl text-lg transition-colors ${
                    snapshot.matchStartConfirmedByMe
                      ? "bg-accent-green/20 text-accent-green border border-accent-green/30 cursor-default"
                      : "bg-accent hover:bg-blue-600 disabled:opacity-50 text-white"
                  }`}
                >
                  {snapshot.matchStartConfirmedByMe
                    ? `Ready (${snapshot.matchStartReadyCount}/${maxPlayers})`
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
        </div>
      )}
    </div>
  );
}
