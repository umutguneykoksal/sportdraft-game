import { submitFeedback } from "./feedbackClient";

describe("feedbackClient", () => {
  beforeEach(() => {
    global.fetch = jest.fn();
  });

  it("submitFeedback postsAnonymousPayload", async () => {
    (global.fetch as jest.Mock).mockResolvedValue({
      ok: true,
      json: async () => ({ id: "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa" }),
    });

    const result = await submitFeedback({
      category: "BUG",
      message: "Something broke during the auction.",
      pagePath: "/lobby",
    });

    expect(result.id).toBe("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    expect(global.fetch).toHaveBeenCalledWith(
      expect.stringContaining("/api/v2/feedback"),
      expect.objectContaining({
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          category: "BUG",
          message: "Something broke during the auction.",
          pagePath: "/lobby",
        }),
      })
    );
  });
});

export {};
