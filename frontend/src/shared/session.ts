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

  const nickname = localStorage.getItem(NICKNAME_KEY) ?? "";

  return { token, nickname };
}

export function saveNickname(nickname: string) {
  localStorage.setItem(NICKNAME_KEY, nickname);
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
