import { getApiBase, getApiPrefix } from "./httpClient";

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
});
