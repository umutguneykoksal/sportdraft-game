import { fetchEventsSince, confirmFormationReady, confirmMatchStart, getSessionSnapshot, pingSession, selectFormation } from "./sessionClient";

describe("sessionClient.fetchEventsSince", () => {
  const originalFetch = global.fetch;

  beforeEach(() => {
    localStorage.setItem("sessionToken", "11111111-1111-1111-1111-111111111111");
  });

  afterEach(() => {
    global.fetch = originalFetch;
    localStorage.clear();
  });

  it("syncEvents_parsesEventsAndLastSeq", async () => {
    global.fetch = jest.fn().mockResolvedValue({
      ok: true,
      json: async () => ({
        events: [{ seq: 2, eventType: "bid_placed" }],
        lastSeq: 2,
      }),
    }) as typeof fetch;

    const response = await fetchEventsSince("session-abc", 1);

    expect(global.fetch).toHaveBeenCalledWith(
      expect.stringContaining("/sessions/session-abc/events/sync?afterSeq=1"),
      { method: "GET" }
    );
    expect(global.fetch).toHaveBeenCalledWith(
      expect.stringContaining("token=11111111-1111-1111-1111-111111111111"),
      { method: "GET" }
    );
    expect(response.events).toHaveLength(1);
    expect(response.lastSeq).toBe(2);
  });

  it("syncEvents_returnsEmptyOnGap", async () => {
    global.fetch = jest.fn().mockResolvedValue({
      ok: true,
      json: async () => ({
        events: [],
        lastSeq: 500,
      }),
    }) as typeof fetch;

    const response = await fetchEventsSince("session-abc", 1, true);

    expect(global.fetch).toHaveBeenCalledWith(
      expect.stringContaining("spectator=true"),
      { method: "GET" }
    );
    expect(response.events).toEqual([]);
    expect(response.lastSeq).toBe(500);
  });

  it("getSessionSnapshot_retriesOnceOnFailure", async () => {
    global.fetch = jest
      .fn()
      .mockRejectedValueOnce(new Error("timeout"))
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({ sessionId: "s1", status: "WAITING", playerCount: 1 }),
      }) as typeof fetch;

    const snapshot = await getSessionSnapshot("session-abc");

    expect(snapshot.sessionId).toBe("s1");
    expect(global.fetch).toHaveBeenCalledTimes(2);
  });

  it("selectFormation_postsFormation", async () => {
    global.fetch = jest.fn().mockResolvedValue({
      ok: true,
      json: async () => ({ sessionId: "s1", status: "FORMATION_SELECT", playerCount: 2 }),
    }) as typeof fetch;

    await selectFormation("s1", "FOUR_FOUR_TWO");

    expect(global.fetch).toHaveBeenCalledWith(
      expect.stringContaining("/sessions/s1/formation"),
      expect.objectContaining({ method: "POST" })
    );
  });

  it("confirmFormationReady_postsReady", async () => {
    global.fetch = jest.fn().mockResolvedValue({
      ok: true,
      json: async () => ({ sessionId: "s1", status: "FORMATION_SELECT", playerCount: 2 }),
    }) as typeof fetch;

    await confirmFormationReady("s1");

    expect(global.fetch).toHaveBeenCalledWith(
      expect.stringContaining("/confirm-formation-ready"),
      expect.objectContaining({ method: "POST" })
    );
  });

  it("confirmMatchStart_postsConfirm", async () => {
    global.fetch = jest.fn().mockResolvedValue({
      ok: true,
      json: async () => ({ sessionId: "s1", status: "MATCH_START_PENDING", playerCount: 2 }),
    }) as typeof fetch;

    await confirmMatchStart("s1");

    expect(global.fetch).toHaveBeenCalledWith(
      expect.stringContaining("/confirm-match-start"),
      expect.objectContaining({ method: "POST" })
    );
  });

  it("pingSession_swallowsErrors", async () => {
    global.fetch = jest.fn().mockRejectedValue(new Error("offline")) as typeof fetch;

    await expect(pingSession("s1")).resolves.toBeUndefined();
  });
});

export {};
