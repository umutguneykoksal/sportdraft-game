import { Browser, expect, Page } from "@playwright/test";
import {
  completeFormationAndStart,
  enterNicknameAndCreateRoom,
  enterNicknameAndJoinRoom,
  expectAuctionPage,
  GameMode,
  readActiveSessionId,
  seedSession,
  SessionSeed,
} from "./lobbyFlow";

const WAITING_TEXT: Record<GameMode, string[]> = {
  DUEL: ["Waiting for 1 more player"],
  TOURNAMENT: [
    "Waiting for 3 more player",
    "Waiting for 2 more player",
    "Waiting for 1 more player",
  ],
  LEAGUE: [
    "Waiting for 3 more player",
    "Waiting for 2 more player",
    "Waiting for 1 more player",
  ],
};

export async function createAndJoinFourPlayerRoom(
  browser: Browser,
  mode: GameMode,
  players: [SessionSeed, SessionSeed, SessionSeed, SessionSeed]
): Promise<{ sessionId: string; pages: Page[] }> {
  const contexts = await Promise.all([
    browser.newContext(),
    browser.newContext(),
    browser.newContext(),
    browser.newContext(),
  ]);
  const pages = await Promise.all(contexts.map((ctx) => ctx.newPage()));
  const [hostPage, guest1Page, guest2Page, guest3Page] = pages;

  for (let i = 0; i < players.length; i++) {
    await seedSession(pages[i], players[i]);
  }

  const [host, guest1, guest2, guest3] = players;

  await enterNicknameAndCreateRoom(hostPage, host.nickname, mode);
  await expect(hostPage.getByText(WAITING_TEXT[mode][0])).toBeVisible({ timeout: 20_000 });

  const sessionId = await readActiveSessionId(hostPage);

  await enterNicknameAndJoinRoom(guest1Page, guest1.nickname, sessionId);
  await expect(hostPage.getByText(WAITING_TEXT[mode][1])).toBeVisible({ timeout: 20_000 });

  await enterNicknameAndJoinRoom(guest2Page, guest2.nickname, sessionId);
  await expect(hostPage.getByText(WAITING_TEXT[mode][2])).toBeVisible({ timeout: 20_000 });

  await enterNicknameAndJoinRoom(guest3Page, guest3.nickname, sessionId);

  for (const page of pages) {
    await completeFormationAndStart(page);
  }

  for (const page of pages) {
    await expectAuctionPage(page);
  }

  return { sessionId, pages };
}
