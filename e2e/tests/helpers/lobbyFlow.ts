import { expect, Page } from "@playwright/test";

export type GameMode = "DUEL" | "TOURNAMENT" | "LEAGUE";

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

export async function selectGameMode(page: Page, mode: GameMode): Promise<void> {
  await page.getByTestId(`lobby-mode-${mode}`).click();
}

export async function enterNicknameAndCreateRoom(
  page: Page,
  nickname: string,
  mode: GameMode = "DUEL"
): Promise<void> {
  await page.goto("/lobby");
  await selectGameMode(page, mode);
  await page.getByTestId("lobby-nickname-input").fill(nickname);
  await page.getByTestId("lobby-create-room").click();
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

export async function readActiveSessionId(page: Page): Promise<string> {
  const sessionId = await page.evaluate(() => localStorage.getItem("activeSessionId"));
  if (!sessionId) {
    throw new Error("activeSessionId was not set in localStorage");
  }
  return sessionId;
}
