import "server-only";
import { apiFetch, ApiError } from "./client";
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

async function nullOn404<T>(promise: Promise<T>): Promise<T | null> {
  try {
    return await promise;
  } catch (e) {
    if (e instanceof ApiError && e.status === 404) return null;
    throw e;
  }
}

export async function getSource(id: string): Promise<Source | null> {
  return nullOn404(apiFetch<Source>(`/api/content/sources/${id}`, { auth: false }));
}

// Returns articles from the paged response, typed as Article via the backend contract.
export type ArticlesPageResult = {
  articles: Article[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

export async function getArticlesPage(
  params: {
    page?: number;
    size?: number;
    sourceId?: string;
    topicId?: string;
    sort?: string;
    q?: string;
  } = {}
): Promise<ArticlesPageResult> {
  const q = new URLSearchParams();
  q.set("page", String(params.page ?? 0));
  q.set("size", String(params.size ?? 20));
  if (params.sourceId) q.set("sourceId", params.sourceId);
  if (params.topicId) q.set("topicId", params.topicId);
  if (params.sort) q.set("sort", params.sort);
  if (params.q) q.set("q", params.q);
  const page = await apiFetch<PageArticle>(`/api/content/articles?${q.toString()}`, { auth: false });
  return {
    articles: (page.content ?? []) as Article[],
    page: page.number ?? params.page ?? 0,
    size: page.size ?? params.size ?? 20,
    totalElements: page.totalElements ?? 0,
    totalPages: page.totalPages ?? 0,
  };
}

export async function getArticles(
  params: {
    page?: number;
    size?: number;
    sourceId?: string;
    topicId?: string;
    sort?: string;
    q?: string;
  } = {}
): Promise<Article[]> {
  return (await getArticlesPage(params)).articles;
}

export async function getArticle(id: string): Promise<Article | null> {
  return nullOn404(apiFetch<Article>(`/api/content/articles/${id}`, { auth: false }));
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
