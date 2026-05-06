export interface ClientSession {
  token: string;
  nickname: string;
}

const TOKEN_KEY = "sessionToken";
const NICKNAME_KEY = "nickname";
const ACTIVE_SESSION_KEY = "activeSessionId";

export function getOrCreateClientSession(): ClientSession {
  let token = localStorage.getItem(TOKEN_KEY);
  if (!token) {
    token = crypto.randomUUID();
    localStorage.setItem(TOKEN_KEY, token);
  }

  let nickname = localStorage.getItem(NICKNAME_KEY);
  if (!nickname) {
    nickname = `Player-${token.slice(0, 6)}`;
    localStorage.setItem(NICKNAME_KEY, nickname);
  }

  return { token, nickname };
}

export function setActiveSessionId(sessionId: string) {
  localStorage.setItem(ACTIVE_SESSION_KEY, sessionId);
}

export function getActiveSessionId(): string | null {
  return localStorage.getItem(ACTIVE_SESSION_KEY);
}

export function clearActiveSessionId() {
  localStorage.removeItem(ACTIVE_SESSION_KEY);
}
