import { readErrorMessage } from "./errors";

describe("readErrorMessage", () => {
  it("returns fallback when body is empty", async () => {
    const response = { text: async () => "" } as Response;
    await expect(readErrorMessage(response, "fallback")).resolves.toBe("fallback");
  });

  it("prefers detail from json body", async () => {
    const response = {
      text: async () => JSON.stringify({ detail: "conflict", message: "ignored" }),
    } as Response;
    await expect(readErrorMessage(response, "fallback")).resolves.toBe("conflict");
  });

  it("returns raw text when json parsing fails", async () => {
    const response = { text: async () => "plain error" } as Response;
    await expect(readErrorMessage(response, "fallback")).resolves.toBe("plain error");
  });
});

export {};
