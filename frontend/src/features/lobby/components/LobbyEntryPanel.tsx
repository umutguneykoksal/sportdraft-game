import { type KeyboardEvent } from "react";
import {
  Eye,
  Globe,
  Loader2,
  PlusCircle,
  RefreshCw,
  Shield,
  Swords,
  Trophy,
  UserRound,
  Users,
} from "lucide-react";
import type { LucideIcon } from "lucide-react";
import type { GameMode, LobbyRoomResponse, PoolLeagueId, PoolMinRating } from "../../../shared/api/contracts";
import {
  GAME_MODE_LABELS,
  MIN_POOL_CARD_COUNT,
  POOL_LEAGUE_OPTIONS,
  POOL_MIN_RATING_OPTIONS,
  SESSION_STATUS_LABELS,
  poolLeagueLabel,
} from "../../../shared/api/contracts";
import type { PoolMinRatingOption } from "../../../shared/api/contracts";
import { cn } from "../../../shared/utils/cn";

const STATUS_BADGE_COLORS: Record<string, string> = {
  WAITING: "bg-emerald-500/20 text-emerald-400 border-emerald-500/30",
  COUNTDOWN: "bg-amber-500/20 text-amber-400 border-amber-500/30",
  FORMATION_SELECT: "bg-blue-500/20 text-blue-400 border-blue-500/30",
  AUCTIONING: "bg-purple-500/20 text-purple-400 border-purple-500/30",
  MATCH_START_PENDING: "bg-orange-500/20 text-orange-400 border-orange-500/30",
  FINALIZING: "bg-slate-500/20 text-slate-400 border-slate-500/30",
  AWAITING_RESULT: "bg-slate-500/20 text-slate-400 border-slate-500/30",
};

const GAME_MODE_INFO: Record<GameMode, { shortLabel: string; icon: LucideIcon }> = {
  DUEL: { shortLabel: "Duel", icon: Swords },
  TOURNAMENT: { shortLabel: "Cup", icon: Trophy },
  LEAGUE: { shortLabel: "League", icon: Shield },
};

function playerInitial(nickname: string): string {
  const trimmed = nickname.trim();
  return trimmed.length > 0 ? trimmed.charAt(0).toUpperCase() : "?";
}

function StatusBadge({ status }: { status: string }) {
  const colors = STATUS_BADGE_COLORS[status] ?? "bg-slate-500/20 text-slate-400 border-slate-500/30";
  const label = SESSION_STATUS_LABELS[status as keyof typeof SESSION_STATUS_LABELS] ?? status;
  return (
    <span className={cn("shrink-0 rounded-full border px-1.5 py-0.5 text-[10px] font-medium", colors)}>
      {label}
    </span>
  );
}

function GameModeChip({
  mode,
  selected,
  onSelect,
}: {
  mode: GameMode;
  selected: boolean;
  onSelect: () => void;
}) {
  const info = GAME_MODE_INFO[mode];
  const Icon = info.icon;

  return (
    <button
      type="button"
      data-testid={`lobby-mode-${mode}`}
      onClick={onSelect}
      className={cn(
        "inline-flex flex-1 items-center justify-center gap-1 rounded-md border px-1 py-1.5 text-[11px] font-semibold transition-colors",
        selected
          ? "border-accent bg-accent/15 text-white"
          : "border-white/10 bg-surface-elevated/60 text-slate-300 hover:border-white/25"
      )}
    >
      <Icon className="h-3 w-3 shrink-0" aria-hidden="true" />
      {info.shortLabel}
    </button>
  );
}

function RoomRow({
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
  const maxPlayers = room.maxPlayers ?? 2;
  const canJoin = room.status === "WAITING" && room.playerCount < maxPlayers;
  const canSpectate = room.status !== "WAITING" && room.status !== "COUNTDOWN";

  return (
    <div className="flex items-center gap-2 rounded-md border border-white/10 bg-surface-elevated/40 px-2 py-1.5">
      <div className="flex -space-x-1 shrink-0">
        {room.playerNicknames.length > 0 ? (
          room.playerNicknames.slice(0, maxPlayers).map((name) => (
            <span
              key={name}
              title={name}
              className="inline-flex h-6 w-6 items-center justify-center rounded-full border border-surface-card bg-accent/20 text-[9px] font-bold text-accent"
            >
              {playerInitial(name)}
            </span>
          ))
        ) : (
          <span className="inline-flex h-6 w-6 items-center justify-center rounded-full border border-white/10 bg-white/5 text-slate-500">
            <Users className="h-3 w-3" aria-hidden="true" />
          </span>
        )}
      </div>

      <div className="min-w-0 flex-1">
        <p className="truncate text-xs font-medium text-white">
          {room.playerNicknames.length > 0 ? room.playerNicknames.join(" vs ") : "Empty room"}
        </p>
        <div className="mt-0.5 flex items-center gap-1">
          <StatusBadge status={room.status} />
          <span className="truncate text-[10px] text-slate-500">
            {room.playerCount}/{maxPlayers}
            {room.gameMode ? ` · ${GAME_MODE_LABELS[room.gameMode as GameMode] ?? room.gameMode}` : ""}
          </span>
        </div>
      </div>

      <div className="shrink-0">
        {canJoin && (
          <button
            data-testid={`lobby-join-${room.sessionId}`}
            onClick={onJoin}
            disabled={loading}
            className="rounded-md bg-accent px-2 py-1 text-[11px] font-semibold text-white hover:bg-blue-600 disabled:opacity-50"
          >
            Join
          </button>
        )}
        {canSpectate && (
          <button
            onClick={onSpectate}
            disabled={loading}
            className="inline-flex items-center gap-0.5 rounded-md border border-white/10 px-2 py-1 text-[11px] font-semibold text-slate-300 hover:bg-white/10 disabled:opacity-50"
          >
            <Eye className="h-3 w-3" aria-hidden="true" />
          </button>
        )}
        {!canJoin && !canSpectate && room.status === "COUNTDOWN" && (
          <Loader2 className="h-3.5 w-3.5 animate-spin text-amber-400" aria-label="Starting" />
        )}
      </div>
    </div>
  );
}

export interface LobbyEntryPanelProps {
  nicknameInput: string;
  onNicknameChange: (value: string) => void;
  nicknameReady: boolean;
  createGameMode: GameMode;
  onCreateGameModeChange: (mode: GameMode) => void;
  createPoolLeague: PoolLeagueId;
  onCreatePoolLeagueChange: (league: PoolLeagueId) => void;
  createMinPoolRating: PoolMinRating;
  onCreateMinPoolRatingChange: (rating: PoolMinRating) => void;
  poolMinRatingOptions: PoolMinRatingOption[];
  poolMinRatingOptionsLoaded: boolean;
  hasSelectablePoolRating: boolean;
  canCreateRoom: boolean;
  loading: boolean;
  rooms: LobbyRoomResponse[];
  onCreateRoom: () => void;
  onJoinRoom: (sessionId: string) => void;
  onSpectateRoom: (sessionId: string) => void;
}

export function LobbyEntryPanel({
  nicknameInput,
  onNicknameChange,
  nicknameReady,
  createGameMode,
  onCreateGameModeChange,
  createPoolLeague,
  onCreatePoolLeagueChange,
  createMinPoolRating,
  onCreateMinPoolRatingChange,
  poolMinRatingOptions,
  poolMinRatingOptionsLoaded,
  hasSelectablePoolRating,
  canCreateRoom,
  loading,
  rooms,
  onCreateRoom,
  onJoinRoom,
  onSpectateRoom,
}: LobbyEntryPanelProps) {
  const onNicknameKeyDown = (e: KeyboardEvent<HTMLInputElement>) => {
    if (e.key === "Enter" && canCreateRoom && !loading) {
      onCreateRoom();
    }
  };

  const nicknameError = nicknameInput.trim().length > 0 && nicknameInput.trim().length < 2;
  const poolError = poolMinRatingOptionsLoaded && !hasSelectablePoolRating;

  return (
    <div className="flex h-full w-full max-w-5xl flex-col overflow-hidden rounded-2xl border border-white/10 bg-surface-card/95 shadow-2xl shadow-black/30 backdrop-blur-sm">
      {/* Card header */}
      <header className="flex shrink-0 items-center gap-2.5 border-b border-white/10 px-4 py-2.5 sm:px-5">
        <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-gradient-to-br from-accent to-blue-700">
          <span className="text-xs font-black text-white">SD</span>
        </div>
        <div className="min-w-0 flex-1">
          <h1 className="text-base font-bold text-white sm:text-lg">SportDraft</h1>
          <p className="truncate text-[11px] text-slate-400">Bid · Build · Play</p>
        </div>
      </header>

      {/* Card body — two columns, fills remaining height */}
      <div className="grid min-h-0 flex-1 grid-cols-2 divide-x divide-white/10">
        {/* Left: create */}
        <section className="flex min-h-0 flex-col p-3 sm:p-4">
          <h2 className="mb-2 text-xs font-semibold uppercase tracking-wide text-slate-400">New game</h2>

          <div className="relative mb-2">
            <UserRound
              className="pointer-events-none absolute left-2.5 top-1/2 h-3.5 w-3.5 -translate-y-1/2 text-slate-500"
              aria-hidden="true"
            />
            <input
              id="lobby-nickname-input"
              data-testid="lobby-nickname-input"
              value={nicknameInput}
              onChange={(e) => onNicknameChange(e.target.value)}
              onKeyDown={onNicknameKeyDown}
              maxLength={20}
              placeholder="Nickname"
              autoComplete="nickname"
              aria-invalid={nicknameError}
              className="w-full rounded-md border border-white/10 bg-surface-elevated py-2 pl-8 pr-10 text-sm text-white placeholder-slate-500 focus:outline-none focus:ring-2 focus:ring-accent"
            />
            <span className="absolute right-2.5 top-1/2 -translate-y-1/2 text-[10px] tabular-nums text-slate-500">
              {nicknameInput.length}/20
            </span>
          </div>
          {nicknameError && (
            <p className="mb-2 text-[11px] text-red-400">Min 2 characters</p>
          )}

          <div className="mb-2 flex gap-1">
            {(["DUEL", "TOURNAMENT", "LEAGUE"] as GameMode[]).map((mode) => (
              <GameModeChip
                key={mode}
                mode={mode}
                selected={createGameMode === mode}
                onSelect={() => onCreateGameModeChange(mode)}
              />
            ))}
          </div>

          <div className="mb-2 flex gap-2">
            <div className="min-w-0 flex-1">
              <label htmlFor="lobby-pool-league" className="mb-1 flex items-center gap-1 text-[10px] font-medium text-slate-400">
                <Globe className="h-3 w-3" aria-hidden="true" />
                League
              </label>
              <select
                id="lobby-pool-league"
                value={createPoolLeague}
                onChange={(e) => onCreatePoolLeagueChange(e.target.value as PoolLeagueId)}
                className="w-full rounded-md border border-white/10 bg-surface-elevated px-2 py-1.5 text-xs text-white focus:outline-none focus:ring-2 focus:ring-accent"
              >
                {POOL_LEAGUE_OPTIONS.map((option) => (
                  <option key={option.id} value={option.id} disabled={!option.enabled}>
                    {option.label}{!option.enabled ? " (soon)" : ""}
                  </option>
                ))}
              </select>
            </div>
            <div className="shrink-0">
              <p className="mb-1 text-[10px] font-medium text-slate-400">OVR</p>
              <div className="flex gap-1">
                {POOL_MIN_RATING_OPTIONS.map((rating) => {
                  const option = poolMinRatingOptions.find((o) => o.minRating === rating);
                  const selectable = !poolMinRatingOptionsLoaded || (option?.selectable ?? false);
                  return (
                    <button
                      key={rating}
                      type="button"
                      onClick={() => selectable && onCreateMinPoolRatingChange(rating)}
                      disabled={!selectable}
                      className={cn(
                        "rounded-md border px-2 py-1.5 text-[11px] font-semibold",
                        createMinPoolRating === rating
                          ? "border-card-gold/50 bg-card-gold/20 text-card-gold"
                          : selectable
                            ? "border-white/10 bg-surface-elevated text-slate-300"
                            : "cursor-not-allowed border-white/5 text-slate-600"
                      )}
                    >
                      {rating}+
                    </button>
                  );
                })}
              </div>
            </div>
          </div>

          {poolError && (
            <p className="mb-2 text-[11px] text-red-400">
              Not enough {poolLeagueLabel(createPoolLeague)} players (need {MIN_POOL_CARD_COUNT}+).
            </p>
          )}

          <button
            data-testid="lobby-create-room"
            onClick={onCreateRoom}
            disabled={loading || !canCreateRoom}
            className="mt-auto inline-flex w-full items-center justify-center gap-1.5 rounded-md bg-accent py-2 text-sm font-bold text-white hover:bg-blue-600 disabled:cursor-not-allowed disabled:opacity-50"
          >
            {loading ? (
              <>
                <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" />
                Creating...
              </>
            ) : (
              <>
                <PlusCircle className="h-4 w-4" aria-hidden="true" />
                Create Room
              </>
            )}
          </button>
        </section>

        {/* Right: rooms */}
        <section className="flex min-h-0 flex-col p-3 sm:p-4">
          <div className="mb-2 flex shrink-0 items-center justify-between">
            <div className="flex items-center gap-1.5">
              <h2 className="text-xs font-semibold uppercase tracking-wide text-slate-400">Join a room</h2>
              {rooms.length > 0 && (
                <span className="rounded-full bg-white/10 px-1.5 py-0.5 text-[10px] text-slate-300">
                  {rooms.length}
                </span>
              )}
            </div>
            <span className="inline-flex items-center gap-1 text-[10px] text-slate-500">
              <RefreshCw className="h-3 w-3" aria-hidden="true" />
              live
            </span>
          </div>

          {!nicknameReady && (
            <p className="mb-2 shrink-0 text-[11px] text-slate-500">Enter a nickname to join</p>
          )}

          {rooms.length === 0 ? (
            <div className="flex flex-1 flex-col items-center justify-center rounded-md border border-dashed border-white/10 px-3 text-center">
              <Users className="mb-1.5 h-6 w-6 text-slate-600" aria-hidden="true" />
              <p className="text-xs font-medium text-slate-400">No rooms open</p>
              <p className="mt-0.5 text-[10px] text-slate-600">Create one to start</p>
            </div>
          ) : (
            <div className="min-h-0 flex-1 space-y-1.5 overflow-y-auto">
              {rooms.map((room) => (
                <RoomRow
                  key={room.sessionId}
                  room={room}
                  onJoin={() => onJoinRoom(room.sessionId)}
                  onSpectate={() => onSpectateRoom(room.sessionId)}
                  loading={loading || !nicknameReady}
                />
              ))}
            </div>
          )}
        </section>
      </div>
    </div>
  );
}
