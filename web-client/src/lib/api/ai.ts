import "server-only";

import { apiFetch } from "./client";

export type SummaryLength = "short" | "medium" | "long";
export type KnowledgeLevel = "child" | "beginner" | "intermediate" | "expert";
export type SentimentLabel = "positive" | "neutral" | "negative";
export type BiasLabel = "left" | "center" | "right" | "unclear";

export type SummarizeResponse = {
  summary: string;
  model: string;
  provider: string;
};

export type ExplainResponse = {
  explanation: string;
  knowledgeLevel: string;
  model: string;
  provider: string;
};

export type SentimentResponse = {
  sentiment: SentimentLabel;
  score: number;
  bias: BiasLabel | null;
  rationale: string;
  model: string;
  provider: string;
};

export type QaResponse = {
  answer: string;
  model: string;
  provider: string;
};

function postJson(body: unknown): RequestInit & { auth: false } {
  return {
    method: "POST",
    auth: false,
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  };
}

export async function summarizeArticle(
  articleId: string,
  length: SummaryLength = "short"
): Promise<SummarizeResponse> {
  return apiFetch<SummarizeResponse>(
    "/api/ai/summarize",
    postJson({ articleId, length })
  );
}

export async function explainArticle(
  articleId: string,
  knowledgeLevel: KnowledgeLevel = "beginner"
): Promise<ExplainResponse> {
  return apiFetch<ExplainResponse>(
    "/api/ai/explain",
    postJson({ articleId, knowledgeLevel })
  );
}

export async function analyzeSentiment(articleId: string): Promise<SentimentResponse> {
  return apiFetch<SentimentResponse>("/api/ai/sentiment", postJson({ articleId }));
}

export async function askArticle(articleId: string, question: string): Promise<QaResponse> {
  return apiFetch<QaResponse>("/api/ai/qa", postJson({ articleId, question }));
}
