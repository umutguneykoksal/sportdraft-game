import { createRoom, joinRoom, leaveLobby, listLobbies, getPoolMinRatingOptions, getSpectateState, getSpectateMatchTimeline } from "./lobbyClient";

describe("lobbyClient", () => {
  const originalFetch = global.fetch;

  beforeEach(() => {
    localStorage.setItem("sessionToken", "11111111-1111-1111-1111-111111111111");
  });

  afterEach(() => {
    global.fetch = originalFetch;
    localStorage.clear();
  });

  it("listLobbies_fetchesLobbyList", async () => {
    global.fetch = jest.fn().mockResolvedValue({
      ok: true,
      json: async () => [],
    }) as typeof fetch;

    const rooms = await listLobbies();

    expect(rooms).toEqual([]);
    expect(global.fetch).toHaveBeenCalledWith(
      expect.stringContaining("/lobbies"),
      expect.objectContaining({ headers: expect.any(Object) })
    );
  });

  it("createRoom_postsPayload", async () => {
    global.fetch = jest.fn().mockResolvedValue({
      ok: true,
      json: async () => ({ sessionId: "s1", status: "WAITING", playerCount: 1 }),
    }) as typeof fetch;

    const snapshot = await createRoom("Host");

    expect(snapshot.sessionId).toBe("s1");
    expect(global.fetch).toHaveBeenCalledWith(
      expect.stringContaining("/lobbies"),
      expect.objectContaining({ method: "POST" })
    );
  });

  it("joinRoom_postsToSessionJoin", async () => {
    global.fetch = jest.fn().mockResolvedValue({
      ok: true,
      json: async () => ({ sessionId: "s1", status: "WAITING", playerCount: 2 }),
    }) as typeof fetch;

    await joinRoom("s1", "Guest");

    expect(global.fetch).toHaveBeenCalledWith(
      expect.stringContaining("/lobbies/s1/join"),
      expect.objectContaining({ method: "POST" })
    );
  });

  it("leaveLobby_deletesPlayerMembership", async () => {
    global.fetch = jest.fn().mockResolvedValue({
      ok: true,
      json: async () => ({ sessionId: "s1", status: "WAITING", playerCount: 0 }),
    }) as typeof fetch;

    await leaveLobby("s1");

    expect(global.fetch).toHaveBeenCalledWith(
      expect.stringContaining("/sessions/s1/players/me"),
      expect.objectContaining({ method: "DELETE" })
    );
  });

  it("getPoolMinRatingOptions_includesLeagueQuery", async () => {
    global.fetch = jest.fn().mockResolvedValue({
      ok: true,
      json: async () => ({ poolLeague: "PREMIER_LEAGUE", options: [] }),
    }) as typeof fetch;

    await getPoolMinRatingOptions("PREMIER_LEAGUE");

    expect(global.fetch).toHaveBeenCalledWith(
      expect.stringContaining("poolLeague=PREMIER_LEAGUE"),
      expect.any(Object)
    );
  });

  it("getSpectateState_fetchesSpectateSnapshot", async () => {
    global.fetch = jest.fn().mockResolvedValue({
      ok: true,
      json: async () => ({ status: "AUCTIONING", homeNickname: "H", awayNickname: "A" }),
    }) as typeof fetch;

    const state = await getSpectateState("s1");

    expect(state.status).toBe("AUCTIONING");
  });

  it("getSpectateMatchTimeline_returnsEmptyOn404", async () => {
    global.fetch = jest.fn().mockResolvedValue({ status: 404, ok: false }) as typeof fetch;

    const timeline = await getSpectateMatchTimeline("s1");

    expect(timeline).toEqual([]);
  });
});

export {};
