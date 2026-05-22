import { createTransport } from "./transportFactory";

describe("transportFactory", () => {
  it("creates sse transport when preferred", () => {
    const transport = createTransport("sse", "ws://x", "http://x/events", {
      onOpen: () => {},
      onClose: () => {},
      onError: () => {},
      onMessage: () => {},
    });
    expect(transport).toBeDefined();
  });

  it("creates websocket transport when preferred", () => {
    const transport = createTransport("websocket", "ws://x", "http://x/events", {
      onOpen: () => {},
      onClose: () => {},
      onError: () => {},
      onMessage: () => {},
    });
    expect(transport).toBeDefined();
  });
});
