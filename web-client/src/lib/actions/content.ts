"use server";

import { revalidatePath } from "next/cache";
import { apiFetch, ApiError } from "@/lib/api/client";
import { getMySettings } from "@/lib/api/reads";
import type { UserSettings } from "@/lib/api/types";

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
    return { ok: true };
  } catch (e) {
    return { ok: false, error: e instanceof ApiError ? e.message : "Could not subscribe" };
  }
}

export async function unsubscribe(sourceId: string): Promise<ActionResult> {
  try {
    await apiFetch(`/api/users/users/me/subscriptions/${sourceId}`, { method: "DELETE" });
    revalidatePath("/settings");
    return { ok: true };
  } catch (e) {
    return { ok: false, error: e instanceof ApiError ? e.message : "Could not unsubscribe" };
  }
}
