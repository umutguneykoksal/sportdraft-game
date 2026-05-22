import "@testing-library/jest-dom";

jest.mock("sonner", () => ({
  toast: {
    error: jest.fn(),
    loading: jest.fn(),
    dismiss: jest.fn(),
  },
  Toaster: () => null,
}));

if (typeof global.crypto === "undefined") {
  Object.defineProperty(global, "crypto", {
    value: {
      randomUUID: () => "11111111-1111-1111-1111-111111111111",
    },
  });
}
