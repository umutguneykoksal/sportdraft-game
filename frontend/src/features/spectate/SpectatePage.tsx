import { useEffect, useRef, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import type {
  AuctionTeamCardResponse,
  Formation,
} from "../../shared/api/contracts";
import { ROUND_STATUS_LABELS } from "../../shared/api/contracts";
import { TeamPitch } from "../../shared/components/TeamPitch";
import { PositionBadgeRow } from "../../shared/components/PositionBadgeRow";
import { PlayerAttributeBars } from "../../shared/components/PlayerAttributeBars";
import { FORMATION_LABELS } from "../../shared/formations";
import { openEligibleHighlights } from "../../shared/positions";
import {
  computeDisplaySecondsLeft,
  ROUND_BID_SECONDS,
} from "../auction/auctionTiming";
import { useSpectateSync } from "../../shared/realtime/useSpectateSync";
import { deriveSpectateTurnMessage, nicknameForUserId } from "./deriveSpectateTurnInfo";
import { useSpectateMatchReplay } from "./hooks/useSpectateMatchReplay";
import { MatchCommentary } from "../match/components/MatchCommentary";

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

  const { state, round, lastEvent, isConnected, loadFailed, matchCompleted } = useSpectateSync(sessionId ?? null);
  const { phase: matchPhase, timeline, simulationStalled, showFinalResult } = useSpectateMatchReplay(
    sessionId ?? null,
    state?.status,
    matchCompleted
  );

  const [homeTeam, setHomeTeam] = useState<AuctionTeamCardResponse[]>([]);
  const [awayTeam, setAwayTeam] = useState<AuctionTeamCardResponse[]>([]);
  const [homeFormation, setHomeFormation] = useState<Formation>("FOUR_FOUR_TWO");
  const [awayFormation, setAwayFormation] = useState<Formation>("FOUR_FOUR_TWO");
  const [secondsLeft, setSecondsLeft] = useState(0);
  const [turnIsOpen, setTurnIsOpen] = useState(false);
  const serverClockOffsetMsRef = useRef(0);
  const roundTotalSecondsRef = useRef(ROUND_BID_SECONDS);
  const previousRoundIdRef = useRef<string | null>(null);
  const wasEverConnectedRef = useRef(false);

  if (isConnected) wasEverConnectedRef.current = true;
  const showReconnecting = wasEverConnectedRef.current && !isConnected;

  useEffect(() => {
    if (!state) return;
    setHomeTeam(mapCards(state.homeTeam));
    setAwayTeam(mapCards(state.awayTeam));
    if (state.homeFormation) setHomeFormation(state.homeFormation as Formation);
    if (state.awayFormation) setAwayFormation(state.awayFormation as Formation);
  }, [state]);

  useEffect(() => {
    if (!lastEvent) return;
    const serverEventTime = Date.parse(lastEvent.occurredAt);
    if (!Number.isNaN(serverEventTime)) {
      serverClockOffsetMsRef.current = serverEventTime - Date.now();
    }
  }, [lastEvent]);

  useEffect(() => {
    if (loadFailed) navigate("/lobby");
  }, [loadFailed, navigate]);

  useEffect(() => {
    if (!round) {
      setSecondsLeft(0);
      return;
    }
    const computeSecondsLeft = () =>
      computeDisplaySecondsLeft(round.startsAt, round.endsAt, serverClockOffsetMsRef.current);
    const initial = computeSecondsLeft();
    if (round.roundId !== previousRoundIdRef.current) {
      roundTotalSecondsRef.current = ROUND_BID_SECONDS;
      previousRoundIdRef.current = round.roundId;
    } else if (initial > roundTotalSecondsRef.current) {
      roundTotalSecondsRef.current = initial;
    }
    setSecondsLeft(initial);
    const timer = setInterval(() => setSecondsLeft(computeSecondsLeft()), 250);
    return () => clearInterval(timer);
  }, [round?.roundId, round?.endsAt, round?.startsAt]);

  useEffect(() => {
    if (!round || !round.turnOpenAt) {
      setTurnIsOpen(false);
      return;
    }
    const check = () => {
      const adjustedNow = Date.now() + serverClockOffsetMsRef.current;
      return adjustedNow >= new Date(round.turnOpenAt!).getTime();
    };
    if (check()) {
      setTurnIsOpen(true);
      return;
    }
    setTurnIsOpen(false);
    const timer = setInterval(() => {
      if (check()) {
        setTurnIsOpen(true);
        clearInterval(timer);
      }
    }, 250);
    return () => clearInterval(timer);
  }, [round?.roundId, round?.turnOpenAt]);

  if (!sessionId) return <div className="p-8 text-white">Session ID missing</div>;
  if (!state) {
    return (
      <div className="min-h-screen bg-surface flex items-center justify-center">
        <p className="text-slate-400 animate-pulse text-lg">Loading spectate view...</p>
      </div>
    );
  }

  const timerPercent =
    round && roundTotalSecondsRef.current > 0
      ? Math.min(100, (secondsLeft / roundTotalSecondsRef.current) * 100)
      : 0;
  const timerColor = secondsLeft <= 5 ? "bg-red-500" : secondsLeft <= 10 ? "bg-amber-500" : "bg-accent";

  const isAuctioning = state.status === "AUCTIONING";
  const showAuctionLayout = matchPhase === "auction" && isAuctioning;
  const homeWon = state.homeScore > state.awayScore;
  const awayWon = state.awayScore > state.homeScore;

  const pitchHighlights = openEligibleHighlights(homeFormation, homeTeam, round?.eligiblePositions);
  const turnInfo =
    round && isAuctioning ? deriveSpectateTurnMessage(round, state, turnIsOpen) : null;
  const highestBidderNickname = round ? nicknameForUserId(round.highestBidderUserId, state) : null;
  const activeTurnUserId = turnIsOpen ? null : round?.currentTurnUserId ?? null;
  const homeIsActive = activeTurnUserId === state.homeUserId;
  const awayIsActive = activeTurnUserId === state.awayUserId;
  const turnToneClass =
    turnInfo?.tone === "open"
      ? "bg-amber-500/10 text-amber-400"
      : turnInfo?.tone === "active"
        ? "bg-accent/10 text-accent"
        : turnInfo?.tone === "leading"
          ? "bg-accent-green/10 text-accent-green"
          : "bg-surface-elevated text-slate-400";

  return (
    <div className="min-h-screen bg-surface">
      <div className="bg-surface-card border-b border-white/10 px-4 py-3">
        <div className="max-w-7xl mx-auto flex flex-wrap items-center justify-between gap-3">
          <div className="flex items-center gap-3">
            <button
              onClick={() => navigate("/lobby")}
              className="text-slate-400 hover:text-white text-sm transition-colors"
            >
              &larr; Back to Lobby
            </button>
            <h1 className="text-lg sm:text-xl font-bold text-white">
              {matchPhase === "commentary"
                ? "Match In Progress"
                : matchPhase === "result"
                  ? "Match Result"
                  : "Spectating"}
            </h1>
            {showAuctionLayout && (
              <span className="text-xs bg-purple-500/20 text-purple-400 border border-purple-500/30 px-2.5 py-1 rounded-full font-medium">
                LIVE
              </span>
            )}
          </div>
          <div className="flex items-center gap-4">
            <div
              className={`text-center rounded-lg px-2 py-1 transition-colors ${
                homeIsActive ? "bg-accent/10 ring-1 ring-accent/40" : ""
              }`}
            >
              <p className={`text-xs ${homeIsActive ? "text-accent font-semibold" : "text-slate-400"}`}>
                {state.homeNickname}
                {homeIsActive ? " · bidding" : ""}
              </p>
              <p className="text-sm font-bold text-white">{state.homeBudget} cr</p>
            </div>
            <span className="text-slate-600">vs</span>
            <div
              className={`text-center rounded-lg px-2 py-1 transition-colors ${
                awayIsActive ? "bg-accent/10 ring-1 ring-accent/40" : ""
              }`}
            >
              <p className={`text-xs ${awayIsActive ? "text-accent font-semibold" : "text-slate-400"}`}>
                {state.awayNickname}
                {awayIsActive ? " · bidding" : ""}
              </p>
              <p className="text-sm font-bold text-white">{state.awayBudget} cr</p>
            </div>
            {showReconnecting && (
              <span className="text-amber-400 text-xs animate-pulse">Reconnecting...</span>
            )}
          </div>
        </div>
      </div>

      {(matchPhase === "simulating" || matchPhase === "loading_replay") && (
        <div className="flex-1 flex items-center justify-center px-4 py-16">
          <div className="animate-pulse text-center">
            <p className="text-xl font-bold text-white">
              {matchPhase === "simulating" ? "Simulating Match..." : "Loading match replay..."}
            </p>
            <p className="text-slate-400 text-sm mt-1">
              {simulationStalled
                ? "Taking longer than expected — still waiting on the server."
                : "Results coming shortly"}
            </p>
            {simulationStalled && (
              <p className="text-amber-400/90 text-xs mt-3 max-w-sm mx-auto">
                If this persists, refresh the page or return to the lobby. The match may still finish on the server.
              </p>
            )}
            <div className="mt-4 flex justify-center gap-3">
              <div className="w-3 h-3 bg-accent rounded-full animate-bounce" />
              <div className="w-3 h-3 bg-accent rounded-full animate-bounce" style={{ animationDelay: "0.1s" }} />
              <div className="w-3 h-3 bg-accent rounded-full animate-bounce" style={{ animationDelay: "0.2s" }} />
            </div>
          </div>
        </div>
      )}

      {matchPhase === "commentary" && timeline.length > 0 && (
        <div className="max-w-3xl mx-auto px-4 py-6 w-full">
          <MatchCommentary
            timeline={timeline}
            homeTeamId={state.homeUserId}
            onComplete={showFinalResult}
            finalHomeScore={state.homeScore}
            finalAwayScore={state.awayScore}
            homeNickname={state.homeNickname}
            awayNickname={state.awayNickname}
          />
        </div>
      )}

      {matchPhase === "result" && (
        <>
          <div className="bg-surface-card border-b border-white/10">
            <div className="max-w-2xl mx-auto px-4 py-8 text-center">
              <div className="flex items-center justify-center gap-8">
                <div className="text-center">
                  <p className="text-sm text-slate-400 mb-1 truncate max-w-32">{state.homeNickname}</p>
                  <p className={`text-7xl font-black ${homeWon ? "text-accent-green" : awayWon ? "text-red-400" : "text-white"}`}>
                    {state.homeScore}
                  </p>
                </div>
                <div className="text-3xl font-bold text-slate-500">-</div>
                <div className="text-center">
                  <p className="text-sm text-slate-400 mb-1 truncate max-w-32">{state.awayNickname}</p>
                  <p className={`text-7xl font-black ${awayWon ? "text-accent-green" : homeWon ? "text-red-400" : "text-white"}`}>
                    {state.awayScore}
                  </p>
                </div>
              </div>
              <p className="text-slate-400 text-sm mt-4">Match Finished</p>
            </div>
          </div>

          <div className="max-w-6xl mx-auto px-4 py-6 w-full">
            <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
              <div className={`bg-surface-card rounded-xl border overflow-hidden ${homeWon ? "border-accent-green/50" : "border-white/10"}`}>
                <div className="px-4 py-3 border-b border-white/10 flex items-center justify-between">
                  <h3 className="font-bold text-white">{state.homeNickname}</h3>
                  <span className="text-sm text-slate-400">{FORMATION_LABELS[homeFormation] ?? homeFormation}</span>
                </div>
                <div className="p-2">
                  <TeamPitch formation={homeFormation} cards={homeTeam} teamColor="#3b82f6" teamBorderColor="#60a5fa" showAmount />
                </div>
              </div>
              <div className={`bg-surface-card rounded-xl border overflow-hidden ${awayWon ? "border-accent-green/50" : "border-white/10"}`}>
                <div className="px-4 py-3 border-b border-white/10 flex items-center justify-between">
                  <h3 className="font-bold text-white">{state.awayNickname}</h3>
                  <span className="text-sm text-slate-400">{FORMATION_LABELS[awayFormation] ?? awayFormation}</span>
                </div>
                <div className="p-2">
                  <TeamPitch formation={awayFormation} cards={awayTeam} away teamColor="#ef4444" teamBorderColor="#f87171" showAmount />
                </div>
              </div>
            </div>
            <div className="text-center mt-8">
              <button
                onClick={() => navigate("/lobby")}
                className="bg-accent hover:bg-blue-600 text-white font-bold py-3 px-8 rounded-xl text-lg transition-colors"
              >
                Back to Lobby
              </button>
            </div>
          </div>
        </>
      )}

      {showAuctionLayout && (
      <div className="max-w-7xl mx-auto px-4 py-6">
        <div className="grid grid-cols-1 lg:grid-cols-[1fr_340px_1fr] gap-6">
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

                <div className="bg-surface-elevated rounded-lg p-3 mb-4 text-center transition-all duration-300">
                  <p className="text-xs text-slate-400">Highest Bid</p>
                  <p
                    key={round.highestBidCents}
                    className="text-2xl font-bold text-white animate-bid-pop"
                  >
                    {round.highestBidCents > 0 ? `${round.highestBidCents} cr` : "No bids"}
                  </p>
                  {highestBidderNickname && round.highestBidCents > 0 && (
                    <p className="text-sm text-accent mt-1">{highestBidderNickname}</p>
                  )}
                  <p className="text-xs text-slate-400 mt-1 transition-opacity duration-300">
                    {ROUND_STATUS_LABELS[round.status] ?? round.status}
                  </p>
                </div>

                <div className={`text-center text-sm font-medium py-2 rounded-lg transition-colors duration-300 ${turnToneClass}`}>
                  {turnInfo?.message ?? "Waiting..."}
                </div>
              </div>
            )}

            {isAuctioning && !round && (
              <div className="bg-surface-card rounded-xl border border-white/10 p-6 text-center">
                <p className="text-slate-400 animate-pulse">Starting next round...</p>
              </div>
            )}
          </div>

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
      )}
    </div>
  );
}
