import { ApiError, readErrorMessage } from "./errors";

const API_BASE = process.env.REACT_APP_API_BASE ?? "http://localhost:8080";

export function getApiBase(): string {
  return API_BASE;
}

export function authHeaders(): Record<string, string> {
  const token = localStorage.getItem("sessionToken");
  if (!token) {
    throw new Error("sessionToken not found in localStorage");
  }
  return {
    Authorization: `Bearer ${token}`,
    "Content-Type": "application/json",
  };
}

export async function fetchWithTimeout(url: string, init: RequestInit, timeoutMs: number): Promise<Response> {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  try {
    return await fetch(url, { ...init, signal: controller.signal });
  } finally {
    clearTimeout(timer);
  }
}

export async function parseOrThrow<T>(response: Response, fallback: string): Promise<T> {
  if (!response.ok) {
    throw new ApiError(await readErrorMessage(response, fallback), response.status);
  }
  return (await response.json()) as T;
}
