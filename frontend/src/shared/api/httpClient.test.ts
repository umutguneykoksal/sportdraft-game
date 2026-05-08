import { getApiBase } from "./httpClient";

describe("httpClient", () => {
  it("exposes api base", () => {
    expect(getApiBase()).toBeDefined();
  });
});
