import {
  ackCompetitionMatchWatch,
  getCompetitionBySession,
  openCompetitionMatch,
} from "./competitionClient";

describe("competitionClient", () => {
  const originalFetch = global.fetch;

  beforeEach(() => {
    localStorage.setItem("sessionToken", "test-user-id");
    global.fetch = jest.fn().mockResolvedValue({
      ok: true,
      json: async () => ({ competitionId: "c1", sessionId: "s1", matches: [] }),
    });
  });

  afterEach(() => {
    global.fetch = originalFetch;
    localStorage.removeItem("sessionToken");
  });

  it("loads competition by session", async () => {
    const data = await getCompetitionBySession("session-1");

    expect(data.competitionId).toBe("c1");
    expect(global.fetch).toHaveBeenCalledWith(
      expect.stringContaining("/sessions/session-1/competition"),
      expect.any(Object)
    );
  });

  it("opens competition match via POST", async () => {
    await openCompetitionMatch("session-1", "match-1");

    expect(global.fetch).toHaveBeenCalledWith(
      expect.stringMatching(/\/api\/v2\/sessions\/session-1\/competition\/matches\/match-1\/open$/),
      expect.objectContaining({ method: "POST" })
    );
  });

  it("opens replay with query param", async () => {
    await openCompetitionMatch("session-1", "match-1", true);

    expect(global.fetch).toHaveBeenCalledWith(
      expect.stringContaining("replay=true"),
      expect.any(Object)
    );
  });

  it("acks watch via POST", async () => {
    await ackCompetitionMatchWatch("session-1", "match-1");

    expect(global.fetch).toHaveBeenCalledWith(
      expect.stringMatching(/\/ack-watch$/),
      expect.objectContaining({ method: "POST" })
    );
  });
});
