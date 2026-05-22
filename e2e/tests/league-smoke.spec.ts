import { randomUUID } from "crypto";
import { test } from "@playwright/test";
import { createAndJoinFourPlayerRoom } from "./helpers/fourPlayerFlow";

const shouldRun = Boolean(process.env.E2E_BASE_URL);

test.describe("league smoke", () => {
  test.skip(!shouldRun, "Set E2E_BASE_URL to run browser smoke against a running stack");

  test("four players reach auction draft page", async ({ browser }) => {
    const players = [
      { token: randomUUID(), nickname: "LHost" },
      { token: randomUUID(), nickname: "LG1" },
      { token: randomUUID(), nickname: "LG2" },
      { token: randomUUID(), nickname: "LG3" },
    ] as const;

    const { sessionId, pages } = await createAndJoinFourPlayerRoom(browser, "LEAGUE", [...players]);

    test.info().attach("sessionId", { body: sessionId, contentType: "text/plain" });

    await Promise.all(pages.map((page) => page.context().close()));
  });
});
