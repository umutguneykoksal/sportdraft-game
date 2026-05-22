import { type KeyboardEvent } from "react";
import { motion, type Variants } from "framer-motion";
import {
  CircleDot,
  Eye,
  Gavel,
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

const GAME_MODE_INFO: Record<GameMode, { shortLabel: string; players: string; description: string; icon: LucideIcon }> = {
  DUEL: {
    shortLabel: "Duel",
    players: "2 players",
    description: "Head-to-head auction draft and instant match",
    icon: Swords,
  },
  TOURNAMENT: {
    shortLabel: "Tournament",
    players: "4 players",
    description: "Single-elimination bracket after the draft",
    icon: Trophy,
  },
  LEAGUE: {
    shortLabel: "League",
    players: "4 players",
    description: "Round-robin season with standings table",
    icon: Shield,
  },
};

const HOW_IT_WORKS = [
  { step: "1", label: "Bid on cards", icon: Gavel },
  { step: "2", label: "Build your XI", icon: Users },
  { step: "3", label: "Play the match", icon: CircleDot },
] as const;

const fadeUp: Variants = {
  hidden: { opacity: 0, y: 20 },
  visible: (i: number) => ({
    opacity: 1,
    y: 0,
    transition: { delay: i * 0.08, duration: 0.45, ease: [0.22, 1, 0.36, 1] as const },
  }),
};

function playerInitial(nickname: string): string {
  const trimmed = nickname.trim();
  return trimmed.length > 0 ? trimmed.charAt(0).toUpperCase() : "?";
}

function StatusBadge({ status }: { status: string }) {
  const colors = STATUS_BADGE_COLORS[status] ?? "bg-slate-500/20 text-slate-400 border-slate-500/30";
  const label = SESSION_STATUS_LABELS[status as keyof typeof SESSION_STATUS_LABELS] ?? status;
  return (
    <span className={cn("text-xs font-medium px-2.5 py-1 rounded-full border", colors)}>
      {label}
    </span>
  );
}

function SectionStep({ step, title, subtitle, icon: Icon }: { step: string; title: string; subtitle?: string; icon: LucideIcon }) {
  return (
    <div className="flex items-start gap-3 mb-5">
      <span className="flex shrink-0 items-center justify-center w-8 h-8 rounded-full bg-accent/15 text-accent text-sm font-bold border border-accent/25">
        {step}
      </span>
      <div className="flex-1 min-w-0">
        <div className="flex items-center gap-2">
          <Icon className="w-4 h-4 text-slate-400 shrink-0" aria-hidden="true" />
          <h2 className="text-lg font-semibold text-white leading-tight">{title}</h2>
        </div>
        {subtitle && <p className="text-slate-400 text-sm mt-0.5 ml-6">{subtitle}</p>}
      </div>
    </div>
  );
}

function LobbyHero() {
  return (
    <motion.header
      initial={{ opacity: 0, y: -12 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.5, ease: [0.22, 1, 0.36, 1] }}
      className="text-center mb-10"
    >
      <motion.div
        initial={{ scale: 0.9, opacity: 0 }}
        animate={{ scale: 1, opacity: 1 }}
        transition={{ delay: 0.1, duration: 0.4 }}
        className="inline-flex items-center justify-center w-16 h-16 rounded-2xl bg-gradient-to-br from-accent to-blue-700 shadow-lg shadow-accent/20 mb-5"
      >
        <span className="text-2xl font-black tracking-tighter text-white">SD</span>
      </motion.div>
      <h1 className="text-4xl sm:text-5xl font-extrabold text-white tracking-tight">
        SportDraft
      </h1>
      <p className="text-slate-400 mt-3 text-base sm:text-lg max-w-md mx-auto leading-relaxed">
        Multiplayer football card auction draft — bid smart, fill your formation, win the match.
      </p>
      <div className="flex flex-wrap justify-center gap-2 sm:gap-3 mt-6">
        {HOW_IT_WORKS.map((item, index) => {
          const Icon = item.icon;
          return (
            <motion.span
              key={item.step}
              custom={index}
              initial="hidden"
              animate="visible"
              variants={fadeUp}
              className="inline-flex items-center gap-2 text-xs sm:text-sm text-slate-300 bg-white/5 border border-white/10 rounded-full px-3 py-1.5"
            >
              <Icon className="w-3.5 h-3.5 text-accent" aria-hidden="true" />
              <span className="text-accent font-semibold">{item.step}</span>
              {item.label}
            </motion.span>
          );
        })}
      </div>
    </motion.header>
  );
}

function GameModeCard({
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
    <motion.button
      type="button"
      data-testid={`lobby-mode-${mode}`}
      onClick={onSelect}
      whileHover={{ y: -2 }}
      whileTap={{ scale: 0.98 }}
      className={cn(
        "text-left rounded-xl border-2 p-4 transition-colors duration-200",
        selected
          ? "border-accent bg-accent/10 ring-2 ring-accent/30 shadow-lg shadow-accent/10"
          : "border-white/10 bg-surface-elevated/50 hover:border-white/25 hover:bg-surface-elevated"
      )}
    >
      <div className="flex items-start gap-3">
        <span className={cn(
          "flex shrink-0 items-center justify-center w-9 h-9 rounded-lg",
          selected ? "bg-accent/20 text-blue-200" : "bg-white/5 text-slate-400"
        )}>
          <Icon className="w-4 h-4" aria-hidden="true" />
        </span>
        <div className="min-w-0 flex-1">
          <div className="flex items-center justify-between gap-2 mb-1">
            <span className={cn("font-semibold", selected ? "text-white" : "text-slate-200")}>
              {info.shortLabel}
            </span>
            <span className={cn(
              "text-xs font-medium px-2 py-0.5 rounded-full shrink-0",
              selected ? "bg-accent/20 text-blue-200" : "bg-white/5 text-slate-400"
            )}>
              {info.players}
            </span>
          </div>
          <p className="text-slate-400 text-xs leading-relaxed">{info.description}</p>
        </div>
      </div>
    </motion.button>
  );
}

function RoomCard({
  room,
  onJoin,
  onSpectate,
  loading,
  index,
}: {
  room: LobbyRoomResponse;
  onJoin: () => void;
  onSpectate: () => void;
  loading: boolean;
  index: number;
}) {
  const maxPlayers = room.maxPlayers ?? 2;
  const canJoin = room.status === "WAITING" && room.playerCount < maxPlayers;
  const canSpectate = room.status !== "WAITING" && room.status !== "COUNTDOWN";

  return (
    <motion.div
      custom={index}
      initial="hidden"
      animate="visible"
      variants={fadeUp}
      className="bg-surface-card/90 backdrop-blur-sm rounded-xl border border-white/10 p-4 flex flex-col sm:flex-row items-start sm:items-center gap-4 transition-colors hover:border-white/20 hover:shadow-md hover:shadow-black/20"
    >
      <div className="flex-1 min-w-0">
        <div className="flex items-center gap-2 mb-2 flex-wrap">
          <StatusBadge status={room.status} />
          {room.gameMode && (
            <span className="text-xs font-medium px-2 py-0.5 rounded-full border bg-violet-500/20 text-violet-300 border-violet-500/30">
              {GAME_MODE_LABELS[room.gameMode as GameMode] ?? room.gameMode}
            </span>
          )}
          {room.minPoolRating != null && room.minPoolRating > 0 && (
            <span className="text-xs font-medium px-2 py-0.5 rounded-full border bg-slate-500/20 text-slate-300 border-slate-500/30">
              OVR {room.minPoolRating}+
            </span>
          )}
          {room.poolLeague && (
            <span className="text-xs font-medium px-2 py-0.5 rounded-full border bg-emerald-500/20 text-emerald-300 border-emerald-500/30">
              {poolLeagueLabel(room.poolLeague)}
            </span>
          )}
        </div>
        <div className="flex items-center gap-3 min-w-0">
          {room.playerNicknames.length > 0 ? (
            <div className="flex -space-x-2 shrink-0">
              {room.playerNicknames.slice(0, maxPlayers).map((name) => (
                <span
                  key={name}
                  title={name}
                  className="inline-flex items-center justify-center w-8 h-8 rounded-full bg-accent/20 border-2 border-surface-card text-accent text-xs font-bold"
                >
                  {playerInitial(name)}
                </span>
              ))}
            </div>
          ) : (
            <span className="inline-flex items-center justify-center w-8 h-8 rounded-full bg-white/5 border border-white/10 text-slate-500">
              <Users className="w-3.5 h-3.5" aria-hidden="true" />
            </span>
          )}
          <div className="min-w-0">
            <p className="text-white font-medium truncate">
              {room.playerNicknames.length > 0
                ? room.playerNicknames.join(" vs ")
                : "Waiting for players"}
            </p>
            <p className="text-slate-500 text-xs mt-0.5">
              {room.playerCount}/{maxPlayers} players
              {room.spectatorCount > 0 && ` · ${room.spectatorCount} watching`}
            </p>
          </div>
        </div>
      </div>
      <div className="flex gap-2 shrink-0 w-full sm:w-auto">
        {canJoin && (
          <button
            data-testid={`lobby-join-${room.sessionId}`}
            onClick={onJoin}
            disabled={loading}
            className="flex-1 sm:flex-none inline-flex items-center justify-center gap-2 bg-accent hover:bg-blue-600 disabled:opacity-50 text-white font-semibold py-2.5 px-5 rounded-lg text-sm transition-colors"
          >
            <PlusCircle className="w-4 h-4" aria-hidden="true" />
            Join
          </button>
        )}
        {canSpectate && (
          <button
            onClick={onSpectate}
            disabled={loading}
            className="flex-1 sm:flex-none inline-flex items-center justify-center gap-2 bg-surface-elevated border border-white/10 text-slate-300 hover:bg-white/10 hover:border-white/20 disabled:opacity-50 font-semibold py-2.5 px-5 rounded-lg text-sm transition-colors"
          >
            <Eye className="w-4 h-4" aria-hidden="true" />
            Spectate
          </button>
        )}
        {!canJoin && !canSpectate && room.status === "COUNTDOWN" && (
          <span className="inline-flex items-center gap-2 text-xs text-amber-400/90 py-2.5 px-3 font-medium">
            <Loader2 className="w-3.5 h-3.5 animate-spin" aria-hidden="true" />
            Starting...
          </span>
        )}
      </div>
    </motion.div>
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

  return (
    <>
      <LobbyHero />

      <div className="w-full max-w-3xl space-y-6">
        <motion.section
          custom={0}
          initial="hidden"
          animate="visible"
          variants={fadeUp}
          className="bg-surface-card/90 backdrop-blur-sm rounded-2xl border border-white/10 p-6 shadow-xl shadow-black/10"
        >
          <SectionStep
            step="1"
            title="Choose your nickname"
            subtitle="Shown to other players in the lobby and match"
            icon={UserRound}
          />
          <div className="relative">
            <UserRound
              className="absolute left-4 top-1/2 -translate-y-1/2 w-5 h-5 text-slate-500 pointer-events-none"
              aria-hidden="true"
            />
            <input
              data-testid="lobby-nickname-input"
              value={nicknameInput}
              onChange={(e) => onNicknameChange(e.target.value)}
              onKeyDown={onNicknameKeyDown}
              maxLength={20}
              placeholder="e.g. ManagerX"
              autoComplete="nickname"
              className="w-full bg-surface-elevated border border-white/10 rounded-xl pl-12 pr-14 py-3.5 text-white placeholder-slate-500 focus:outline-none focus:ring-2 focus:ring-accent focus:border-transparent text-lg transition-shadow"
            />
            <span className="absolute right-4 top-1/2 -translate-y-1/2 text-xs text-slate-500 tabular-nums">
              {nicknameInput.length}/20
            </span>
          </div>
          {nicknameInput.trim().length > 0 && nicknameInput.trim().length < 2 && (
            <p className="text-red-400 text-sm mt-2">Nickname must be at least 2 characters</p>
          )}
          {!nicknameReady && nicknameInput.trim().length === 0 && (
            <p className="text-slate-500 text-sm mt-2">
              Required before creating or joining a room
            </p>
          )}
        </motion.section>

        <motion.section
          custom={1}
          initial="hidden"
          animate="visible"
          variants={fadeUp}
          className="bg-surface-card/90 backdrop-blur-sm rounded-2xl border border-white/10 p-6 shadow-xl shadow-black/10"
        >
          <SectionStep
            step="2"
            title="Configure your match"
            subtitle="Pick a mode and card pool for the auction"
            icon={Globe}
          />

          <p className="text-slate-400 text-sm font-medium mb-3">Game mode</p>
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-3 mb-6">
            {(["DUEL", "TOURNAMENT", "LEAGUE"] as GameMode[]).map((mode) => (
              <GameModeCard
                key={mode}
                mode={mode}
                selected={createGameMode === mode}
                onSelect={() => onCreateGameModeChange(mode)}
              />
            ))}
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-5">
            <div>
              <label htmlFor="lobby-pool-league" className="flex items-center gap-2 text-slate-400 text-sm font-medium mb-2">
                <Globe className="w-4 h-4" aria-hidden="true" />
                Card pool — league
              </label>
              <select
                id="lobby-pool-league"
                value={createPoolLeague}
                onChange={(e) => onCreatePoolLeagueChange(e.target.value as PoolLeagueId)}
                className="w-full bg-surface-elevated border border-white/10 rounded-xl px-4 py-3 text-white focus:outline-none focus:ring-2 focus:ring-accent transition-shadow"
              >
                {POOL_LEAGUE_OPTIONS.map((option) => (
                  <option key={option.id} value={option.id} disabled={!option.enabled}>
                    {option.label}{!option.enabled ? " (coming soon)" : ""}
                  </option>
                ))}
              </select>
            </div>

            <div>
              <p className="text-slate-400 text-sm font-medium mb-2">Card pool — minimum overall</p>
              <div className="flex flex-wrap gap-2">
                {POOL_MIN_RATING_OPTIONS.map((rating) => {
                  const option = poolMinRatingOptions.find((o) => o.minRating === rating);
                  const selectable = !poolMinRatingOptionsLoaded || (option?.selectable ?? false);
                  const cardCount = option?.cardCount;
                  return (
                    <button
                      key={rating}
                      type="button"
                      onClick={() => selectable && onCreateMinPoolRatingChange(rating)}
                      disabled={!selectable}
                      title={
                        !selectable && cardCount != null
                          ? `${cardCount} players available (need ${MIN_POOL_CARD_COUNT}+)`
                          : undefined
                      }
                      className={cn(
                        "text-sm font-medium px-3.5 py-2 rounded-lg border transition-colors",
                        createMinPoolRating === rating
                          ? "bg-card-gold/20 border-card-gold/50 text-card-gold"
                          : selectable
                            ? "bg-surface-elevated border-white/10 text-slate-300 hover:border-white/30"
                            : "bg-surface-elevated/50 border-white/5 text-slate-600 cursor-not-allowed"
                      )}
                    >
                      {rating}+ OVR{!selectable && poolMinRatingOptionsLoaded ? " ✕" : ""}
                    </button>
                  );
                })}
              </div>
            </div>
          </div>

          <p className="text-slate-500 text-xs mt-4 leading-relaxed">
            Only {poolLeagueLabel(createPoolLeague)} cards rated {createMinPoolRating} or higher appear in the auction.
            {poolMinRatingOptionsLoaded && (
              <> Each threshold needs at least {MIN_POOL_CARD_COUNT} eligible players.</>
            )}
          </p>

          {poolMinRatingOptionsLoaded && !hasSelectablePoolRating && (
            <p className="text-red-400 text-sm mt-3">
              No rating threshold has enough players for {poolLeagueLabel(createPoolLeague)} (need {MIN_POOL_CARD_COUNT}+).
            </p>
          )}

          <motion.button
            data-testid="lobby-create-room"
            onClick={onCreateRoom}
            disabled={loading || !canCreateRoom}
            whileHover={canCreateRoom && !loading ? { scale: 1.01 } : undefined}
            whileTap={canCreateRoom && !loading ? { scale: 0.99 } : undefined}
            className="w-full mt-6 inline-flex items-center justify-center gap-2 bg-accent hover:bg-blue-600 disabled:opacity-50 disabled:cursor-not-allowed text-white font-bold py-3.5 px-6 rounded-xl text-lg transition-colors shadow-lg shadow-accent/20"
          >
            {loading ? (
              <>
                <Loader2 className="w-5 h-5 animate-spin" aria-hidden="true" />
                Creating room...
              </>
            ) : (
              <>
                <PlusCircle className="w-5 h-5" aria-hidden="true" />
                Create Room
              </>
            )}
          </motion.button>
        </motion.section>

        <motion.section
          custom={2}
          initial="hidden"
          animate="visible"
          variants={fadeUp}
        >
          <div className="flex items-center justify-between mb-4">
            <div className="flex items-center gap-2">
              <h2 className="text-lg font-semibold text-white">Active Rooms</h2>
              {rooms.length > 0 && (
                <span className="text-xs font-medium px-2 py-0.5 rounded-full bg-white/10 text-slate-300">
                  {rooms.length}
                </span>
              )}
            </div>
            <span className="inline-flex items-center gap-1.5 text-slate-500 text-xs">
              <RefreshCw className="w-3 h-3" aria-hidden="true" />
              Updates every 3s
            </span>
          </div>
          {rooms.length === 0 ? (
            <div className="bg-surface-card/60 backdrop-blur-sm rounded-2xl border border-dashed border-white/15 p-10 text-center">
              <div className="inline-flex items-center justify-center w-12 h-12 rounded-full bg-white/5 border border-white/10 mb-4">
                <Users className="w-6 h-6 text-slate-500" aria-hidden="true" />
              </div>
              <p className="text-slate-300 font-medium">No open rooms right now</p>
              <p className="text-slate-500 text-sm mt-1 max-w-xs mx-auto">
                Create a room above and invite friends, or check back in a moment.
              </p>
            </div>
          ) : (
            <div className="space-y-3">
              {rooms.map((room, index) => (
                <RoomCard
                  key={room.sessionId}
                  room={room}
                  onJoin={() => onJoinRoom(room.sessionId)}
                  onSpectate={() => onSpectateRoom(room.sessionId)}
                  loading={loading || !nicknameReady}
                  index={index}
                />
              ))}
            </div>
          )}
        </motion.section>
      </div>
    </>
  );
}
