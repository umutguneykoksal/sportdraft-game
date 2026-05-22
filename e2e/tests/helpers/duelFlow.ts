import { expect, Page } from "@playwright/test";

export interface SessionSeed {
  token: string;
  nickname: string;
}

export async function seedSession(page: Page, seed: SessionSeed): Promise<void> {
  await page.addInitScript(({ token, nickname }) => {
    localStorage.setItem("sessionToken", token);
    localStorage.setItem("nickname", nickname);
  }, seed);
}

export async function enterNicknameAndCreateRoom(page: Page, nickname: string): Promise<void> {
  await page.goto("/lobby");
  await page.getByTestId("lobby-nickname-input").fill(nickname);
  await page.getByTestId("lobby-create-room").click();
  await expect(page.getByText("Waiting for 1 more player")).toBeVisible({ timeout: 20_000 });
}

export async function enterNicknameAndJoinRoom(
  page: Page,
  nickname: string,
  sessionId: string
): Promise<void> {
  await page.goto("/lobby");
  await page.getByTestId("lobby-nickname-input").fill(nickname);
  await page.getByTestId(`lobby-join-${sessionId}`).click({ timeout: 20_000 });
}

export async function completeFormationAndStart(page: Page): Promise<void> {
  await expect(page.getByText("Choose Your Formation")).toBeVisible({ timeout: 25_000 });
  await page.getByTestId("lobby-formation-FOUR_FOUR_TWO").click();
  await page.getByTestId("lobby-confirm-formation-ready").click();
}

export async function expectAuctionPage(page: Page): Promise<void> {
  await expect(page).toHaveURL(/\/auction\//, { timeout: 45_000 });
  await expect(page.getByTestId("auction-page")).toBeVisible();
}

export async function createAndJoinDuel(
  hostPage: Page,
  guestPage: Page,
  host: SessionSeed,
  guest: SessionSeed
): Promise<string> {
  await seedSession(hostPage, host);
  await seedSession(guestPage, guest);

  await enterNicknameAndCreateRoom(hostPage, host.nickname);

  const sessionId = await hostPage.evaluate(() => localStorage.getItem("activeSessionId"));
  if (!sessionId) {
    throw new Error("Host activeSessionId was not set after room creation");
  }

  await enterNicknameAndJoinRoom(guestPage, guest.nickname, sessionId);
  await completeFormationAndStart(hostPage);
  await completeFormationAndStart(guestPage);
  await expectAuctionPage(hostPage);
  await expectAuctionPage(guestPage);

  return sessionId;
}
