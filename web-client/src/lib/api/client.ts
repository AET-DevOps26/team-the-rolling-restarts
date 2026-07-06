import "server-only";
import { cookies } from "next/headers";

import { AUTH_COOKIE } from "@/lib/auth/constants";

export { AUTH_COOKIE };

export class ApiError extends Error {
  readonly name = "ApiError";
  constructor(
    readonly status: number,
    message: string,
    readonly details: string[] = []
  ) {
    super(message);
  }
}

function baseUrl(): string {
  return process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";
}

function normalizeHeaders(init?: HeadersInit): Record<string, string> {
  if (!init) return {};
  if (init instanceof Headers) {
    const out: Record<string, string> = {};
    init.forEach((value, key) => {
      out[key] = value;
    });
    return out;
  }
  if (Array.isArray(init)) return Object.fromEntries(init);
  return { ...init };
}

export type ApiFetchOptions = RequestInit & { auth?: boolean };

export async function apiFetch<T>(path: string, options: ApiFetchOptions = {}): Promise<T> {
  const { auth = true, headers: extraHeaders, ...rest } = options;
  const headers: Record<string, string> = {
    Accept: "application/json",
    ...normalizeHeaders(extraHeaders),
  };

  if (auth) {
    const token = (await cookies()).get(AUTH_COOKIE)?.value;
    if (token) headers.Authorization = `Bearer ${token}`;
  }

  const res = await fetch(`${baseUrl()}${path}`, { ...rest, headers, cache: "no-store" });

  if (!res.ok) {
    let message = res.statusText || "Request failed";
    let details: string[] = [];
    try {
      const body = await res.json();
      if (body?.message) message = body.message;
      if (Array.isArray(body?.details)) details = body.details;
    } catch {
      /* non-JSON error body */
    }
    throw new ApiError(res.status, message, details);
  }

  if (res.status === 204) return undefined as T;

  const text = await res.text();
  if (!text.trim()) return undefined as T;
  return JSON.parse(text) as T;
}
