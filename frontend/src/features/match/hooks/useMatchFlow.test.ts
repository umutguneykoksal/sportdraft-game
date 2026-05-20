import { MATCH_RESULT_POLL_MS, MATCH_REPLAY_POLL_MS } from "../../../shared/realtime/syncConstants";

describe("useMatchFlow", () => {
  it("shares match result poll interval with spectate replay cadence", () => {
    expect(MATCH_RESULT_POLL_MS).toBe(MATCH_REPLAY_POLL_MS);
    expect(MATCH_RESULT_POLL_MS).toBe(2_000);
  });
});

export {};
