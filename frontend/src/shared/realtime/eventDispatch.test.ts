import {
  decideReplayAction,
  mergeRoundFromRest,
  processEventBatch,
} from "./eventDispatch";
import { AuctionRoundResponse } from "../api/contracts";

describe("mergeRoundFromRest", () => {
  const baseRound = (overrides: Partial<AuctionRoundResponse> = {}): AuctionRoundResponse =>
    ({
      roundId: "r1",
      cardId: "c1",
      cardName: "Player",
      position: "FWD",
      status: "OPEN",
      highestBidCents: 100,
      endsAt: "2026-05-18T12:00:30.000Z",
      ...overrides,
    }) as AuctionRoundResponse;

  it("returns incoming when current is null", () => {
    const incoming = baseRound();
    expect(mergeRoundFromRest(null, incoming)).toBe(incoming);
  });

  it("rejects stale lower bid from REST", () => {
    const current = baseRound({ highestBidCents: 500 });
    const incoming = baseRound({ highestBidCents: 200 });
    expect(mergeRoundFromRest(current, incoming)).toBe(current);
  });

  it("rejects same bid with earlier endsAt", () => {
    const current = baseRound({ highestBidCents: 300, endsAt: "2026-05-18T12:00:45.000Z" });
    const incoming = baseRound({ highestBidCents: 300, endsAt: "2026-05-18T12:00:30.000Z" });
    expect(mergeRoundFromRest(current, incoming)).toBe(current);
  });

  it("rejects stale lower session version from REST", () => {
    const current = baseRound({ version: 5, highestBidCents: 100 });
    const incoming = baseRound({ version: 3, highestBidCents: 500 });
    expect(mergeRoundFromRest(current, incoming)).toBe(current);
  });
});

describe("decideReplayAction", () => {
  it("returns full when server seq is behind client", () => {
    expect(decideReplayAction(10, 5, 0)).toBe("full");
  });

  it("returns replay when missed events exist", () => {
    expect(decideReplayAction(10, 12, 2)).toBe("replay");
  });

  it("returns full when server seq advanced with no replay buffer", () => {
    expect(decideReplayAction(10, 15, 0)).toBe("full");
  });

  it("returns none when in sync", () => {
    expect(decideReplayAction(10, 10, 0)).toBe("none");
  });
});

describe("processEventBatch", () => {
  const round = {
    roundId: "r1",
    highestBidCents: 200,
    status: "OPEN",
  } as AuctionRoundResponse;

  it("patches bid_placed and requests state refresh for player budgets", () => {
    const patchRound = jest.fn();
    const { result } = processEventBatch(
      [{ eventType: "bid_placed", seq: 5, payload: { round }, occurredAt: new Date().toISOString() }],
      4,
      {
        patchRound,
        clearRound: jest.fn(),
        setRound: jest.fn(),
        onMatchCompleted: jest.fn(),
      }
    );
    expect(patchRound).toHaveBeenCalledWith(round, true);
    expect(result.refreshKind).toBe("state");
  });

  it("requests state refresh on SOLD round_ended for players", () => {
    const { result } = processEventBatch(
      [{ eventType: "round_ended", seq: 6, payload: { result: "SOLD" }, occurredAt: new Date().toISOString() }],
      5,
      {
        patchRound: jest.fn(),
        clearRound: jest.fn(),
        setRound: jest.fn(),
        onMatchCompleted: jest.fn(),
      }
    );
    expect(result.refreshKind).toBe("state");
  });

  it("requests full refresh on SOLD round_ended for spectators", () => {
    const { result } = processEventBatch(
      [{ eventType: "round_ended", seq: 6, payload: { result: "SOLD" }, occurredAt: new Date().toISOString() }],
      5,
      {
        patchRound: jest.fn(),
        clearRound: jest.fn(),
        setRound: jest.fn(),
        onMatchCompleted: jest.fn(),
      },
      { spectate: true }
    );
    expect(result.refreshKind).toBe("full");
  });

  it("invokes onRoundEnded for each round_ended even when followed by round_started", () => {
    const onRoundEnded = jest.fn();
    const soldRound = {
      roundId: "r1",
      sessionId: "s1",
      cardId: "c1",
      cardName: "Haaland",
      status: "SOLD",
      highestBidCents: 10,
    };
    const nextRound = { ...soldRound, roundId: "r2", cardName: "Salah", status: "OPEN" };
    processEventBatch(
      [
        {
          eventType: "round_ended",
          seq: 5,
          payload: { result: "SOLD", round: soldRound, winnerUserId: "u1" },
          occurredAt: new Date().toISOString(),
        },
        {
          eventType: "round_started",
          seq: 6,
          payload: { round: nextRound },
          occurredAt: new Date().toISOString(),
        },
      ],
      4,
      {
        patchRound: jest.fn(),
        clearRound: jest.fn(),
        setRound: jest.fn(),
        onMatchCompleted: jest.fn(),
        onRoundEnded,
      }
    );
    expect(onRoundEnded).toHaveBeenCalledTimes(1);
    expect(onRoundEnded).toHaveBeenCalledWith(
      expect.objectContaining({ result: "SOLD", winnerUserId: "u1" })
    );
  });

  it("patches round and requests state refresh on PASSED round_ended", () => {
    const clearRound = jest.fn();
    const patchRound = jest.fn();
    const endedRound = { roundId: "r1", status: "PASSED", cardName: "Haaland" };
    const { result } = processEventBatch(
      [{
        eventType: "round_ended",
        seq: 6,
        payload: { result: "PASSED", round: endedRound },
        occurredAt: new Date().toISOString(),
      }],
      5,
      {
        patchRound,
        clearRound,
        setRound: jest.fn(),
        onMatchCompleted: jest.fn(),
      }
    );
    expect(clearRound).not.toHaveBeenCalled();
    expect(patchRound).toHaveBeenCalledWith(endedRound, false);
    expect(result.refreshKind).toBe("state");
  });
});
