import { getApiBase, getApiPrefix, parseOrThrow } from "./httpClient";

describe("httpClient", () => {
  const originalVersion = process.env.REACT_APP_API_VERSION;

  afterEach(() => {
    if (originalVersion === undefined) {
      delete process.env.REACT_APP_API_VERSION;
    } else {
      process.env.REACT_APP_API_VERSION = originalVersion;
    }
  });

  it("exposes api base", () => {
    expect(getApiBase()).toBeDefined();
  });

  it("defaults api prefix to v2", () => {
    delete process.env.REACT_APP_API_VERSION;
    expect(getApiPrefix()).toBe("/api/v2");
  });

  it("supports v1 rollback prefix", () => {
    process.env.REACT_APP_API_VERSION = "v1";
    expect(getApiPrefix()).toBe("/api");
  });

  it("parseOrThrow_returnsJsonOnSuccess", async () => {
    const response = {
      ok: true,
      json: async () => ({ ok: true }),
    } as Response;

    await expect(parseOrThrow(response, "failed")).resolves.toEqual({ ok: true });
  });

  it("parseOrThrow_throwsOnErrorStatus", async () => {
    const response = {
      ok: false,
      status: 409,
      text: async () => JSON.stringify({ message: "conflict" }),
    } as Response;

    await expect(parseOrThrow(response, "failed")).rejects.toThrow("conflict");
  });
});
