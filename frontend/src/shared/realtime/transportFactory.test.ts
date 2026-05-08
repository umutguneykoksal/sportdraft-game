import { createTransport } from "./transportFactory";

describe("transportFactory", () => {
  it("creates websocket transport when preferred", () => {
    const transport = createTransport("websocket", "ws://x", "http://x", {
      onOpen: () => {},
      onClose: () => {},
      onError: () => {},
      onMessage: () => {},
    });
    expect(transport).toBeDefined();
  });
});
