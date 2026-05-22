/** Opt into React Router v7 behavior and silence v6 deprecation warnings in tests/dev. */
export const REACT_ROUTER_FUTURE_FLAGS = {
  v7_startTransition: true,
  v7_relativeSplatPath: true,
} as const;
