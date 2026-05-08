import { confirmMatchStart, passRound, placeBid, readyNextRound, startRound } from "../../../shared/api";

export function useAuctionCommands(sessionId: string) {
  return {
    startRound: () => startRound(sessionId),
    placeBid: (roundId: string, amountCents: number) => placeBid(sessionId, roundId, amountCents),
    passRound: (roundId: string) => passRound(sessionId, roundId),
    readyNextRound: (roundId: string) => readyNextRound(sessionId, roundId),
    confirmMatchStart: () => confirmMatchStart(sessionId),
  };
}
