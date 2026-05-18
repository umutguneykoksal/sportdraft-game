import { useEffect, useRef, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import type {
  AuctionRoundResponse,
  AuctionTeamCardResponse,
  SpectateStateResponse,
  Formation,
  Position,
} from "../../shared/api/contracts";
import { ROUND_STATUS_LABELS } from "../../shared/api/contracts";
import { getSpectateState } from "../../shared/api/lobbyClient";
import { useSpectateStream } from "../../shared/sse";
import { TeamPitch } from "../../shared/components/TeamPitch";
import { PositionBadgeRow } from "../../shared/components/PositionBadgeRow";
import { PlayerAttributeBars } from "../../shared/components/PlayerAttributeBars";
import { FORMATION_LABELS } from "../../shared/formations";
import { openEligibleHighlights } from "../../shared/positions";

function mapCards(raw: AuctionTeamCardResponse[]): AuctionTeamCardResponse[] {
  return raw.map((c) => ({
    cardId: c.cardId,
    cardName: c.cardName,
    position: c.position,
    rating: c.rating ?? 0,
    amountCents: c.amountCents,
  }));
}

const positionColors: Record<string, string> = {
  GK: "bg-yellow-500",
  DEF: "bg-blue-500",
  CDM: "bg-indigo-500",
  CM: "bg-green-500",
  CAM: "bg-orange-500",
  FWD: "bg-red-500",
};

export function SpectatePage() {
  const { sessionId } = useParams<{ sessionId: string }>();
  const navigate = useNavigate();

  const [state, setState] = useState<SpectateStateResponse | null>(null);
  const [round, setRound] = useState<AuctionRoundResponse | null>(null);
  const [homeTeam, setHomeTeam] = useState<AuctionTeamCardResponse[]>([]);
  const [awayTeam, setAwayTeam] = useState<AuctionTeamCardResponse[]>([]);
  const [homeFormation, setHomeFormation] = useState<Formation>("FOUR_FOUR_TWO");
  const [awayFormation, setAwayFormation] = useState<Formation>("FOUR_FOUR_TWO");
  const [secondsLeft, setSecondsLeft] = useState(0);
  const serverClockOffsetMsRef = useRef(0);
  const roundTotalSecondsRef = useRef(15);
  const prevConnectedRef = useRef(false);
  const wasEverConnectedRef = useRef(false);
  const syncSeqRef = useRef(0);

  const { events, isConnected } = useSpectateStream(sessionId ?? null);
  if (isConnected) wasEverConnectedRef.current = true;
  const showReconnecting = wasEverConnectedRef.current && !isConnected;

  const applyState = (s: SpectateStateResponse) => {
    setState(s);
    setRound(s.currentRound);
    setHomeTeam(mapCards(s.homeTeam));
    setAwayTeam(mapCards(s.awayTeam));
    if (s.homeFormation) setHomeFormation(s.homeFormation as Formation);
    if (s.awayFormation) setAwayFormation(s.awayFormation as Formation);
  };

  // 1. Initial load
  useEffect(() => {
    if (!sessionId) return;
    let cancelled = false;
    (async () => {
      const seq = ++syncSeqRef.current;
      try {
        const s = await getSpectateState(sessionId);
        if (cancelled || seq !== syncSeqRef.current) return;
        applyState(s);
      } catch {
        if (!cancelled) navigate("/lobby");
      }
    })();
    return () => { cancelled = true; };
  }, [sessionId, navigate]);

  // 2. Poll fallback
  useEffect(() => {
    if (!sessionId) return;
    let cancelled = false;
    const intervalMs = isConnected ? 30000 : 3000;
    const poll = async () => {
      const seq = ++syncSeqRef.current;
      try {
        const s = await getSpectateState(sessionId);
        if (cancelled || seq !== syncSeqRef.current) return;
        applyState(s);
      } catch { /* transient */ }
    };
    const id = window.setInterval(poll, intervalMs);
    return () => { cancelled = true; window.clearInterval(id); };
  }, [sessionId, isConnected]);

  // 3. SSE reconnection catch-up
  useEffect(() => {
    const wasConnected = prevConnectedRef.current;
    prevConnectedRef.current = isConnected;
    if (!sessionId || !isConnected || !wasConnected) return;
    (async () => {
      const seq = ++syncSeqRef.current;
      try {
        const s = await getSpectateState(sessionId);
        if (seq !== syncSeqRef.current) return;
        applyState(s);
      } catch { /* ignore */ }
    })();
  }, [isConnected, sessionId]);

  // 4. SSE event handler
  useEffect(() => {
    const latest = events.at(-1);
    if (!latest || !sessionId) return;
    const payload = latest.payload as { round?: AuctionRoundResponse };

    if (
      (latest.eventType === "round_started" || latest.eventType === "bid_placed"
        || latest.eventType === "round_ended" || latest.eventType === "round_ready"
        || latest.eventType === "turn_passed")
      && payload.round
    ) {
      const serverEventTime = Date.parse(latest.occurredAt);
      if (!Number.isNaN(serverEventTime)) serverClockOffsetMsRef.current = serverEventTime - Date.now();
      setRound(payload.round);
    }

    if (latest.eventType === "round_ended" || latest.eventType === "match_start_pending"
      || latest.eventType === "match_start_ready" || latest.eventType === "session_finalizing") {
      (async () => {
        const seq = ++syncSeqRef.current;
        try {
          const s = await getSpectateState(sessionId);
          if (seq !== syncSeqRef.current) return;
          applyState(s);
        } catch { /* ignore */ }
      })();
    }

    if (latest.eventType === "match_completed") {
      (async () => {
        try {
          const s = await getSpectateState(sessionId);
          applyState(s);
        } catch { /* ignore */ }
      })();
    }
  }, [events, sessionId]);

  // 5. Round countdown
  useEffect(() => {
    if (!round) { setSecondsLeft(0); return; }
    const computeSecondsLeft = () => {
      const adjustedNow = Date.now() + serverClockOffsetMsRef.current;
      return Math.max(0, Math.ceil((new Date(round.endsAt).getTime() - adjustedNow) / 1000));
    };
    const initial = computeSecondsLeft();
    if (initial > roundTotalSecondsRef.current) roundTotalSecondsRef.current = initial;
    setSecondsLeft(initial);
    const timer = setInterval(() => setSecondsLeft(computeSecondsLeft()), 250);
    return () => clearInterval(timer);
  }, [round]);

  if (!sessionId) return <div className="p-8 text-white">Session ID missing</div>;
  if (!state) return (
    <div className="min-h-screen bg-surface flex items-center justify-center">
      <p className="text-slate-400 animate-pulse text-lg">Loading spectate view...</p>
    </div>
  );

  const timerPercent = round && roundTotalSecondsRef.current > 0
    ? Math.min(100, (secondsLeft / roundTotalSecondsRef.current) * 100)
    : 0;
  const timerColor = secondsLeft <= 5 ? "bg-red-500" : secondsLeft <= 10 ? "bg-amber-500" : "bg-accent";

  const isAuctioning = state.status === "AUCTIONING";
  const isFinished = state.status === "FINISHED";

  const pitchHighlights = openEligibleHighlights(homeFormation, homeTeam, round?.eligiblePositions);

  return (
    <div className="min-h-screen bg-surface">
      {/* Top Bar */}
      <div className="bg-surface-card border-b border-white/10 px-4 py-3">
        <div className="max-w-7xl mx-auto flex flex-wrap items-center justify-between gap-3">
          <div className="flex items-center gap-3">
            <button
              onClick={() => navigate("/lobby")}
              className="text-slate-400 hover:text-white text-sm transition-colors"
            >
              &larr; Back to Lobby
            </button>
            <h1 className="text-lg sm:text-xl font-bold text-white">Spectating</h1>
            <span className="text-xs bg-purple-500/20 text-purple-400 border border-purple-500/30 px-2.5 py-1 rounded-full font-medium">
              LIVE
            </span>
          </div>
          <div className="flex items-center gap-4">
            <div className="text-center">
              <p className="text-xs text-slate-400">{state.homeNickname}</p>
              <p className="text-sm font-bold text-white">{state.homeBudget} cr</p>
            </div>
            <span className="text-slate-600">vs</span>
            <div className="text-center">
              <p className="text-xs text-slate-400">{state.awayNickname}</p>
              <p className="text-sm font-bold text-white">{state.awayBudget} cr</p>
            </div>
            {showReconnecting && (
              <span className="text-amber-400 text-xs animate-pulse">Reconnecting...</span>
            )}
          </div>
        </div>
      </div>

      {/* Match result banner */}
      {isFinished && (
        <div className="bg-surface-card border-b border-white/10 px-4 py-6 text-center">
          <p className="text-2xl font-extrabold text-white">
            {state.homeNickname} {state.homeScore} - {state.awayScore} {state.awayNickname}
          </p>
          <p className="text-slate-400 text-sm mt-1">Match Finished</p>
        </div>
      )}

      {/* Simulating */}
      {(state.status === "AWAITING_RESULT" || state.status === "FINALIZING") && (
        <div className="bg-surface-card border-b border-white/10 px-4 py-6 text-center">
          <div className="animate-pulse">
            <p className="text-xl font-bold text-white">Simulating Match...</p>
            <p className="text-slate-400 text-sm mt-1">Results coming shortly</p>
          </div>
        </div>
      )}

      {/* Main 3-Column Layout */}
      <div className="max-w-7xl mx-auto px-4 py-6">
        <div className="grid grid-cols-1 lg:grid-cols-[1fr_340px_1fr] gap-6">

          {/* LEFT: Home Team */}
          <div className="bg-surface-card rounded-xl border border-white/10 overflow-hidden">
            <div className="px-4 py-3 border-b border-white/10 flex items-center justify-between">
              <h3 className="font-bold text-white">{state.homeNickname}</h3>
              <div className="flex items-center gap-2">
                <span className="text-xs text-slate-400">{FORMATION_LABELS[homeFormation] ?? homeFormation}</span>
                <span className="text-sm text-slate-400">{homeTeam.length}/11</span>
              </div>
            </div>
            <div className="p-2">
              <TeamPitch
                formation={homeFormation}
                cards={homeTeam}
                teamColor="#3b82f6"
                teamBorderColor="#60a5fa"
                highlightPositions={pitchHighlights}
              />
            </div>
            <div className="px-4 py-2 border-t border-white/10 max-h-40 overflow-y-auto">
              {homeTeam.length === 0 ? (
                <p className="text-slate-500 text-sm text-center py-2">No players yet</p>
              ) : (
                <div className="space-y-1">
                  {homeTeam.map((c) => (
                    <div key={c.cardId} className="flex items-center justify-between text-sm">
                      <div className="flex items-center gap-2">
                        <span className={`${positionColors[c.position] ?? "bg-gray-500"} text-white text-xs px-1.5 py-0.5 rounded font-bold`}>
                          {c.position}
                        </span>
                        <span className="text-white">{c.cardName}</span>
                        <span className="text-slate-500">{c.rating}</span>
                      </div>
                      <span className="text-slate-400">{c.amountCents} cr</span>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </div>

          {/* CENTER: Auction Panel (read-only) */}
          <div className="space-y-4">
            {round && isAuctioning && (
              <div key={round.roundId} className="bg-surface-card rounded-xl border border-white/10 p-4 animate-fade-in">
                <div className="text-center mb-4">
                  <PositionBadgeRow
                    primary={round.position}
                    eligiblePositions={round.eligiblePositions}
                    colors={positionColors}
                  />
                  <h2 className="text-2xl font-extrabold text-white mt-2">{round.cardName}</h2>
                  <p className="text-4xl font-black text-card-gold mt-1">{round.cardRating ?? "?"}</p>
                  <p className="text-xs text-slate-400 mt-1">Overall</p>
                  {round.pace != null && (
                    <PlayerAttributeBars
                      pace={round.pace}
                      shooting={round.shooting ?? 0}
                      passing={round.passing ?? 0}
                      defending={round.defending ?? 0}
                      physical={round.physical ?? 0}
                    />
                  )}
                </div>

                {/* Timer Bar */}
                <div className="mb-4">
                  <div className="flex justify-between text-xs text-slate-400 mb-1">
                    <span>Time Left</span>
                    <span className={`font-bold transition-colors duration-300 ${secondsLeft <= 5 ? "text-red-400" : "text-white"}`}>
                      {secondsLeft}s
                    </span>
                  </div>
                  <div className="w-full h-2 bg-surface-elevated rounded-full overflow-hidden">
                    <div
                      className={`h-full rounded-full transition-all duration-300 ${timerColor}`}
                      style={{ width: `${timerPercent}%` }}
                    />
                  </div>
                </div>

                {/* Current Bid */}
                <div className="bg-surface-elevated rounded-lg p-3 mb-4 text-center transition-all duration-300">
                  <p className="text-xs text-slate-400">Highest Bid</p>
                  <p
                    key={round.highestBidCents}
                    className="text-2xl font-bold text-white animate-bid-pop"
                  >
                    {round.highestBidCents > 0
                      ? `${round.highestBidCents} cr`
                      : "No bids"}
                  </p>
                  <p className="text-xs text-slate-400 mt-1 transition-opacity duration-300">
                    {ROUND_STATUS_LABELS[round.status] ?? round.status}
                  </p>
                </div>

                {/* Turn Indicator (spectator view) */}
                <div className="text-center text-sm font-medium py-2 rounded-lg bg-surface-elevated text-slate-400">
                  {round.currentTurnUserId ? "Bidding in progress..." : "Waiting..."}
                </div>
              </div>
            )}

            {isAuctioning && !round && (
              <div className="bg-surface-card rounded-xl border border-white/10 p-6 text-center">
                <p className="text-slate-400 animate-pulse">Starting next round...</p>
              </div>
            )}
          </div>

          {/* RIGHT: Away Team */}
          <div className="bg-surface-card rounded-xl border border-white/10 overflow-hidden">
            <div className="px-4 py-3 border-b border-white/10 flex items-center justify-between">
              <h3 className="font-bold text-white">{state.awayNickname}</h3>
              <div className="flex items-center gap-2">
                <span className="text-xs text-slate-400">{FORMATION_LABELS[awayFormation] ?? awayFormation}</span>
                <span className="text-sm text-slate-400">{awayTeam.length}/11</span>
              </div>
            </div>
            <div className="p-2">
              <TeamPitch
                formation={awayFormation}
                cards={awayTeam}
                away
                teamColor="#ef4444"
                teamBorderColor="#f87171"
              />
            </div>
            <div className="px-4 py-2 border-t border-white/10 max-h-40 overflow-y-auto">
              {awayTeam.length === 0 ? (
                <p className="text-slate-500 text-sm text-center py-2">No players yet</p>
              ) : (
                <div className="space-y-1">
                  {awayTeam.map((c) => (
                    <div key={c.cardId} className="flex items-center justify-between text-sm">
                      <div className="flex items-center gap-2">
                        <span className={`${positionColors[c.position] ?? "bg-gray-500"} text-white text-xs px-1.5 py-0.5 rounded font-bold`}>
                          {c.position}
                        </span>
                        <span className="text-white">{c.cardName}</span>
                        <span className="text-slate-500">{c.rating}</span>
                      </div>
                      <span className="text-slate-400">{c.amountCents} cr</span>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </div>

        </div>
      </div>
    </div>
  );
}
