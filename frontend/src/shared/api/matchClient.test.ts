import { getMatchResult, getMatchTimeline } from "./matchClient";

describe("matchClient", () => {
  const originalFetch = global.fetch;

  beforeEach(() => {
    localStorage.setItem("sessionToken", "11111111-1111-1111-1111-111111111111");
  });

  afterEach(() => {
    global.fetch = originalFetch;
    localStorage.clear();
  });

  it("getMatchResult_returnsNullOn404", async () => {
    global.fetch = jest.fn().mockResolvedValue({ status: 404, ok: false }) as typeof fetch;

    const result = await getMatchResult("session-1");

    expect(result).toBeNull();
  });

  it("getMatchTimeline_returnsEmptyOn404", async () => {
    global.fetch = jest.fn().mockResolvedValue({ status: 404, ok: false }) as typeof fetch;

    const timeline = await getMatchTimeline("session-1");

    expect(timeline).toEqual([]);
  });
});

export {};
