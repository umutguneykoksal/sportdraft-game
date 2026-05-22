import { randomUUID } from "crypto";
import { test } from "@playwright/test";
import { createAndJoinDuel } from "./helpers/duelFlow";

const shouldRun = Boolean(process.env.E2E_BASE_URL);

test.describe("duel smoke", () => {
  test.skip(!shouldRun, "Set E2E_BASE_URL to run browser smoke against a running stack");

  test("two players reach auction draft page", async ({ browser }) => {
    const hostContext = await browser.newContext();
    const guestContext = await browser.newContext();
    const hostPage = await hostContext.newPage();
    const guestPage = await guestContext.newPage();

    const sessionId = await createAndJoinDuel(
      hostPage,
      guestPage,
      { token: randomUUID(), nickname: "E2EHost" },
      { token: randomUUID(), nickname: "E2EGuest" }
    );

    test.info().attach("sessionId", { body: sessionId, contentType: "text/plain" });

    await hostContext.close();
    await guestContext.close();
  });
});
