import {
  computeDisplaySecondsLeft,
  computeServerSecondsLeft,
  isInCardRevealPhase,
  ROUND_BID_SECONDS,
} from "./auctionTiming";

export type RoundTimerInput = {
  roundId: string;
  startsAt: string | null;
  endsAt: string;
  previousRoundId: string | null;
  previousSecondsLeft: number;
  roundTotalSeconds: number;
  serverClockOffsetMs: number;
  hasGraceTimeout: boolean;
};

export type RoundTimerEffects = {
  secondsLeft: number;
  serverSecondsLeft: number;
  inCardReveal: boolean;
  roundTotalSeconds: number;
  previousSecondsLeft: number;
  previousRoundId: string;
  timeExtended: boolean;
  startGrace: boolean;
  clearGrace: boolean;
};

export function deriveRoundTimerEffects(input: RoundTimerInput): RoundTimerEffects {
  const serverSecondsLeft = computeServerSecondsLeft(input.endsAt, input.serverClockOffsetMs);
  const inCardReveal = isInCardRevealPhase(input.startsAt, input.endsAt, input.serverClockOffsetMs);
  const secondsLeft = computeDisplaySecondsLeft(
    input.startsAt,
    input.endsAt,
    input.serverClockOffsetMs
  );
  const roundChanged = input.previousRoundId !== input.roundId;
  const wasExpired = !roundChanged && input.previousSecondsLeft <= 0;

  let roundTotalSeconds = input.roundTotalSeconds;
  let timeExtended = false;
  let startGrace = false;
  let clearGrace = false;

  if (!roundChanged && wasExpired && serverSecondsLeft > 0) {
    clearGrace = true;
    timeExtended = true;
  }

  if (roundChanged) {
    roundTotalSeconds = ROUND_BID_SECONDS;
    clearGrace = true;
  } else if (secondsLeft > roundTotalSeconds) {
    roundTotalSeconds = secondsLeft;
    if (secondsLeft > 0) {
      clearGrace = true;
    }
  } else if (!roundChanged && wasExpired && serverSecondsLeft <= 0 && !input.hasGraceTimeout) {
    startGrace = true;
  }

  return {
    secondsLeft,
    serverSecondsLeft,
    inCardReveal,
    roundTotalSeconds,
    previousSecondsLeft: secondsLeft,
    previousRoundId: input.roundId,
    timeExtended,
    startGrace,
    clearGrace,
  };
}
