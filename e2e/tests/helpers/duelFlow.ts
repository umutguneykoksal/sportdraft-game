import { expect, Page } from "@playwright/test";
import {
  completeFormationAndStart,
  enterNicknameAndCreateRoom,
  enterNicknameAndJoinRoom,
  expectAuctionPage,
  readActiveSessionId,
  seedSession,
  SessionSeed,
} from "./lobbyFlow";

export type { SessionSeed } from "./lobbyFlow";

export async function createAndJoinDuel(
  hostPage: Page,
  guestPage: Page,
  host: SessionSeed,
  guest: SessionSeed
): Promise<string> {
  await seedSession(hostPage, host);
  await seedSession(guestPage, guest);

  await enterNicknameAndCreateRoom(hostPage, host.nickname, "DUEL");
  await expect(hostPage.getByText("Waiting for 1 more player")).toBeVisible({ timeout: 20_000 });

  const sessionId = await readActiveSessionId(hostPage);

  await enterNicknameAndJoinRoom(guestPage, guest.nickname, sessionId);
  await completeFormationAndStart(hostPage);
  await completeFormationAndStart(guestPage);
  await expectAuctionPage(hostPage);
  await expectAuctionPage(guestPage);

  return sessionId;
}
