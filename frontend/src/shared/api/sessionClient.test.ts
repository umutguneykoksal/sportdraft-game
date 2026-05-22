import { fetchEventsSince } from "./sessionClient";

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
});

export {};
