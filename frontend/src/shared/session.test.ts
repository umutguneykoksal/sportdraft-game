import {
  clearActiveSessionId,
  getActiveSessionId,
  getOrCreateClientSession,
  saveNickname,
  setActiveSessionId,
} from "./session";

describe("session storage helpers", () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it("getOrCreateClientSession_persistsToken", () => {
    const first = getOrCreateClientSession();
    const second = getOrCreateClientSession();

    expect(first.token).toBe(second.token);
    expect(localStorage.getItem("sessionToken")).toBe(first.token);
  });

  it("saveNickname_persistsNickname", () => {
    saveNickname("Captain");
    expect(getOrCreateClientSession().nickname).toBe("Captain");
  });

  it("activeSessionId_roundTrip", () => {
    setActiveSessionId("session-abc");
    expect(getActiveSessionId()).toBe("session-abc");
    clearActiveSessionId();
    expect(getActiveSessionId()).toBeNull();
  });
});

export {};
