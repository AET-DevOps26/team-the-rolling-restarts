"use server";

import { cookies } from "next/headers";
import { redirect } from "next/navigation";
import { apiFetch, ApiError, AUTH_COOKIE } from "@/lib/api/client";
import type { TokenResponse } from "@/lib/api/types";

export type AuthResult = { error: string } | undefined;

const COOKIE_MAX_AGE = 60 * 60; // 1h, matches JWT TTL

async function setToken(token: string) {
  (await cookies()).set(AUTH_COOKIE, token, {
    httpOnly: true,
    secure: process.env.NODE_ENV === "production",
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
    return { error: e instanceof ApiError ? e.message : "Sign in failed" };
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
    const token = await apiFetch<TokenResponse>("/api/users/auth/login", {
      auth: false,
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ username: body.username, password: body.password }),
    });
    if (!token.token) return { error: "Sign up failed" };
    await setToken(token.token);
  } catch (e) {
    return { error: e instanceof ApiError ? e.message : "Sign up failed" };
  }
  redirect("/dashboard");
}

export async function logout(): Promise<void> {
  (await cookies()).delete(AUTH_COOKIE);
  redirect("/");
}
