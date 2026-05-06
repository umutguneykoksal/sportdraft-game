export interface ClientSession {
  token: string;
  nickname: string;
}

const TOKEN_KEY = "sessionToken";
const NICKNAME_KEY = "nickname";

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
