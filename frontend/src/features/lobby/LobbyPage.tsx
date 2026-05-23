import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { toast } from "sonner";
import { useLobbyFlow } from "./hooks/useLobbyFlow";
import { LobbyEntryPanel } from "./components/LobbyEntryPanel";
import { LobbySessionPanel } from "./components/LobbySessionPanel";
import { saveNickname } from "../../shared/session";

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
    <div
      className={`lobby-bg flex p-3 sm:p-4 ${
        isInSession
          ? "min-h-dvh items-center justify-center overflow-hidden"
          : "h-dvh items-center justify-center overflow-hidden"
      }`}
    >
      {!isInSession && (
        <div className="h-[min(680px,calc(100dvh-2rem))] w-full max-w-6xl">
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

      {isInSession && snapshot && (
        <div className="h-[min(680px,calc(100dvh-2rem))] w-full max-w-4xl">
          <LobbySessionPanel
            nickname={session.nickname}
            snapshot={snapshot}
            maxPlayers={maxPlayers}
            gameMode={gameMode}
            countdownSecondsLeft={countdownSecondsLeft}
            formations={formations}
            loading={loading}
            onChooseFormation={chooseFormation}
            onStartAuction={handleStartAuction}
            onLeave={handleLeave}
          />
        </div>
      )}
    </div>
  );
}
