"use server";

import { cookies } from "next/headers";
import { redirect } from "next/navigation";
import { apiFetch, AUTH_COOKIE } from "@/lib/api/client";
import { toApiErrorDisplay } from "@/lib/api/errors";
import type { TokenResponse } from "@/lib/api/types";

export type AuthResult = { error: string; details?: string[] } | undefined;

const COOKIE_MAX_AGE = 60 * 60; // 1h, matches JWT TTL

async function setToken(token: string) {
  (await cookies()).set(AUTH_COOKIE, token, {
    httpOnly: true,
    // Deliberately not NODE_ENV-keyed: that conflates "production build" with "served over
    // HTTPS," which aren't the same thing on the Azure VM deployment target (no TLS there —
    // see issue #90). A Secure cookie is never sent back over a plain HTTP connection, which
    // broke login persistence there. Each deployment target sets this explicitly instead.
    secure: process.env.COOKIE_SECURE === "true",
    sameSite: "lax",
    path: "/",
    maxAge: COOKIE_MAX_AGE,
  });
}

export async function login(_prev: AuthResult, formData: FormData): Promise<AuthResult> {
  const username = String(formData.get("username") ?? "");
  const password = String(formData.get("password") ?? "");
  try {
    const res = await apiFetch<TokenResponse>("/api/users/auth/login", {
      auth: false,
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ username, password }),
    });
    if (!res.token) return { error: "Sign in failed" };
    await setToken(res.token);
  } catch (e) {
    const { message, details } = toApiErrorDisplay(e, "Sign in failed");
    return { error: message, ...(details.length > 0 ? { details } : {}) };
  }
  redirect("/dashboard");
}

export async function register(_prev: AuthResult, formData: FormData): Promise<AuthResult> {
  const body = {
    username: String(formData.get("username") ?? ""),
    email: String(formData.get("email") ?? ""),
    password: String(formData.get("password") ?? ""),
    name: String(formData.get("name") ?? ""),
  };
  try {
    await apiFetch("/api/users/auth/register", {
      auth: false,
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body),
    });
  } catch (e) {
    const { message, details } = toApiErrorDisplay(e, "Sign up failed");
    return { error: message, ...(details.length > 0 ? { details } : {}) };
  }

  try {
    const token = await apiFetch<TokenResponse>("/api/users/auth/login", {
      auth: false,
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ username: body.username, password: body.password }),
    });
    if (!token.token) {
      return { error: "Account created. Please sign in with your new credentials." };
    }
    await setToken(token.token);
  } catch {
    return { error: "Account created. Please sign in with your new credentials." };
  }
  redirect("/dashboard");
}

export async function logout(): Promise<void> {
  (await cookies()).delete(AUTH_COOKIE);
  redirect("/");
}
