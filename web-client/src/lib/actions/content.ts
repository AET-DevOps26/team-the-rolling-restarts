"use server";

import { revalidatePath } from "next/cache";
import { apiFetch, ApiError } from "@/lib/api/client";
import { toApiErrorDisplay } from "@/lib/api/errors";
import { getMySettings } from "@/lib/api/reads";
import type { Source, UserSettings } from "@/lib/api/types";
import { normalizeRssUrl } from "@/lib/format/rss-url";

export type ActionResult = { ok: true } | { ok: false; error: string };

async function putSettings(settings: UserSettings): Promise<void> {
  await apiFetch("/api/users/users/me/settings", {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(settings),
  });
}

export async function saveArticle(articleId: string): Promise<ActionResult> {
  try {
    const settings = await getMySettings();
    if (!settings.savedArticleIds.includes(articleId)) {
      await putSettings({
        ...settings,
        savedArticleIds: [...settings.savedArticleIds, articleId],
      });
    }
    revalidatePath("/saved");
    revalidatePath("/dashboard");
    return { ok: true };
  } catch (e) {
    return { ok: false, error: e instanceof ApiError ? e.message : "Could not save article" };
  }
}

export async function unsaveArticle(articleId: string): Promise<ActionResult> {
  try {
    const settings = await getMySettings();
    await putSettings({
      ...settings,
      savedArticleIds: settings.savedArticleIds.filter((id) => id !== articleId),
    });
    revalidatePath("/saved");
    revalidatePath("/dashboard");
    return { ok: true };
  } catch (e) {
    return { ok: false, error: e instanceof ApiError ? e.message : "Could not remove article" };
  }
}

export async function subscribe(sourceId: string): Promise<ActionResult> {
  try {
    await apiFetch(`/api/users/users/me/subscriptions/${sourceId}`, { method: "POST" });
    revalidatePath("/settings");
    revalidatePath("/dashboard");
    revalidatePath("/", "layout");
    return { ok: true };
  } catch (e) {
    return { ok: false, error: e instanceof ApiError ? e.message : "Could not subscribe" };
  }
}

export async function unsubscribe(sourceId: string): Promise<ActionResult> {
  try {
    await apiFetch(`/api/users/users/me/subscriptions/${sourceId}`, { method: "DELETE" });
    revalidatePath("/settings");
    revalidatePath("/dashboard");
    revalidatePath("/", "layout");
    return { ok: true };
  } catch (e) {
    return { ok: false, error: e instanceof ApiError ? e.message : "Could not unsubscribe" };
  }
}

export type AddSourceResult =
  | { ok: true; sourceId: string }
  | { ok: false; error: string; details?: string[]; sourceId?: string };

export async function addSource(name: string, rssUrl: string): Promise<AddSourceResult> {
  const trimmedName = name.trim();
  const trimmedUrl = normalizeRssUrl(rssUrl);
  if (!trimmedName) return { ok: false, error: "Source name is required" };
  if (!trimmedUrl) return { ok: false, error: "RSS feed URL is required" };

  try {
    const source = await apiFetch<{ id: string }>("/api/content/sources", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ name: trimmedName, rssUrl: trimmedUrl }),
    });
    if (!source?.id) return { ok: false, error: "Could not add source" };

    try {
      await apiFetch(`/api/users/users/me/subscriptions/${source.id}`, { method: "POST" });
    } catch (e) {
      const { message, details } = toApiErrorDisplay(
        e,
        "Source was created but could not be subscribed"
      );
      return {
        ok: false,
        error: message,
        sourceId: source.id,
        ...(details.length > 0 ? { details } : {}),
      };
    }
    revalidatePath("/settings");
    revalidatePath("/", "layout");
    return { ok: true, sourceId: source.id };
  } catch (e) {
    const { message, details } = toApiErrorDisplay(e, "Could not add source");
    return { ok: false, error: message, ...(details.length > 0 ? { details } : {}) };
  }
}

export type SourceFetchStatusResult =
  | { ok: true; status: NonNullable<Source["fetchStatus"]>; error?: string }
  | { ok: false; error: string };

/**
 * Reads a source's current fetch status. The client polls this after adding a source so it can
 * show progress and surface any failure without blocking on the initial fetch.
 */
export async function getSourceFetchStatus(sourceId: string): Promise<SourceFetchStatusResult> {
  try {
    const source = await apiFetch<Pick<Source, "fetchStatus" | "fetchError">>(
      `/api/content/sources/${sourceId}`,
      { auth: false }
    );
    // Legacy sources predate fetch tracking; treat a missing status as ready.
    const status = source.fetchStatus ?? "SUCCESS";
    if (status === "SUCCESS") revalidatePath("/dashboard");
    return { ok: true, status, error: source.fetchError ?? undefined };
  } catch (e) {
    return { ok: false, error: e instanceof ApiError ? e.message : "Could not check fetch status" };
  }
}
