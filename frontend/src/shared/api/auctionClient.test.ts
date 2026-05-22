import { getAuctionState, placeBid } from "./auctionClient";

describe("auctionClient", () => {
  const originalFetch = global.fetch;

  beforeEach(() => {
    localStorage.setItem("sessionToken", "11111111-1111-1111-1111-111111111111");
  });

  afterEach(() => {
    global.fetch = originalFetch;
    localStorage.clear();
  });

  it("placeBid_sendsIdempotencyKey", async () => {
    global.fetch = jest.fn().mockResolvedValue({
      ok: true,
      json: async () => ({ roundId: "r1" }),
    }) as typeof fetch;

    await placeBid("session-1", "round-1", 10);

    expect(global.fetch).toHaveBeenCalledWith(
      expect.stringContaining("/sessions/session-1/bids"),
      expect.objectContaining({
        method: "POST",
        headers: expect.objectContaining({ "Idempotency-Key": expect.any(String) }),
      })
    );
  });

  it("getAuctionState_fetchesState", async () => {
    global.fetch = jest.fn().mockResolvedValue({
      ok: true,
      json: async () => ({ version: 1, myFormation: "FOUR_FOUR_TWO" }),
    }) as typeof fetch;

    const state = await getAuctionState("session-1");

    expect(state.version).toBe(1);
  });
});

export {};
