import "server-only";
import { apiFetch } from "./client";
import type {
  Article,
  PageArticle,
  Source,
  Topic,
  UserProfile,
  UserSettings,
} from "./types";

export async function getTopics(): Promise<Topic[]> {
  return apiFetch<Topic[]>("/api/content/topics", { auth: false });
}

export async function getSources(): Promise<Source[]> {
  return apiFetch<Source[]>("/api/content/sources", { auth: false });
}

export async function getSource(id: string): Promise<Source | null> {
  try {
    return await apiFetch<Source>(`/api/content/sources/${id}`, { auth: false });
  } catch {
    return null;
  }
}

// Returns the page's articles already normalized to the required Article type.
export async function getArticles(params: {
  page?: number;
  size?: number;
  sourceId?: string;
  topicId?: string;
  sort?: string;
} = {}): Promise<Article[]> {
  const q = new URLSearchParams();
  q.set("page", String(params.page ?? 0));
  q.set("size", String(params.size ?? 20));
  if (params.sourceId) q.set("sourceId", params.sourceId);
  if (params.topicId) q.set("topicId", params.topicId);
  if (params.sort) q.set("sort", params.sort);
  const page = await apiFetch<PageArticle>(`/api/content/articles?${q.toString()}`, { auth: false });
  return (page.content ?? []) as Article[];
}

export async function getArticle(id: string): Promise<Article | null> {
  try {
    return await apiFetch<Article>(`/api/content/articles/${id}`, { auth: false });
  } catch {
    return null;
  }
}

export async function getSavedArticles(ids: string[]): Promise<Article[]> {
  if (ids.length === 0) return [];
  return apiFetch<Article[]>("/api/content/articles/saved", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(ids),
  });
}

export async function getMe(): Promise<UserProfile> {
  return apiFetch<UserProfile>("/api/users/users/me");
}

export async function getMySettings(): Promise<UserSettings> {
  return apiFetch<UserSettings>("/api/users/users/me/settings");
}
