export type RoundTimerInput = {
  roundId: string;
  endsAt: string;
  previousRoundId: string | null;
  previousSecondsLeft: number;
  roundTotalSeconds: number;
  serverClockOffsetMs: number;
  hasGraceTimeout: boolean;
};

export type RoundTimerEffects = {
  secondsLeft: number;
  roundTotalSeconds: number;
  previousSecondsLeft: number;
  previousRoundId: string;
  timeExtended: boolean;
  startGrace: boolean;
  clearGrace: boolean;
};

export function deriveRoundTimerEffects(input: RoundTimerInput): RoundTimerEffects {
  const adjustedNow = Date.now() + input.serverClockOffsetMs;
  const secondsLeft = Math.max(0, Math.ceil((new Date(input.endsAt).getTime() - adjustedNow) / 1000));
  const roundChanged = input.previousRoundId !== input.roundId;
  const wasExpired = !roundChanged && input.previousSecondsLeft <= 0;

  let roundTotalSeconds = input.roundTotalSeconds;
  let timeExtended = false;
  let startGrace = false;
  let clearGrace = false;

  if (!roundChanged && wasExpired && secondsLeft > 0) {
    clearGrace = true;
    timeExtended = true;
  }

  if (roundChanged) {
    roundTotalSeconds = secondsLeft;
    clearGrace = true;
  } else if (secondsLeft > roundTotalSeconds) {
    roundTotalSeconds = secondsLeft;
    if (secondsLeft > 0) {
      clearGrace = true;
    }
  } else if (!roundChanged && wasExpired && secondsLeft <= 0 && !input.hasGraceTimeout) {
    startGrace = true;
  }

  return {
    secondsLeft,
    roundTotalSeconds,
    previousSecondsLeft: secondsLeft,
    previousRoundId: input.roundId,
    timeExtended,
    startGrace,
    clearGrace,
  };
}
