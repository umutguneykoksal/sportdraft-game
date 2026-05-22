import React from "react";
import ReactDOM from "react-dom/client";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { BrowserRouter } from "react-router-dom";
import { App } from "./App";
import { REACT_ROUTER_FUTURE_FLAGS } from "./test/reactRouterFuture";
import "./index.css";

const queryClient = new QueryClient();

const rootElement = document.getElementById("root");
if (!rootElement) {
  throw new Error("Root element not found");
}

ReactDOM.createRoot(rootElement).render(
  <React.StrictMode>
    <QueryClientProvider client={queryClient}>
      <BrowserRouter future={REACT_ROUTER_FUTURE_FLAGS}>
        <App />
      </BrowserRouter>
    </QueryClientProvider>
  </React.StrictMode>
);
