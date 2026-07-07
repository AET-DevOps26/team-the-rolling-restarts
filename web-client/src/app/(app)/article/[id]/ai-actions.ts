"use server";

import { ApiError } from "@/lib/api/client";
import {
  analyzeSentiment,
  askArticle,
  explainArticle,
  summarizeArticle,
  type ExplainResponse,
  type KnowledgeLevel,
  type QaResponse,
  type SentimentResponse,
  type SummarizeResponse,
  type SummaryLength,
} from "@/lib/api/ai";

export type AiActionResult<T> = { ok: true; data: T } | { ok: false; error: string };

function toErrorMessage(error: unknown): string {
  if (error instanceof ApiError) return error.message;
  return "Something went wrong. Please try again.";
}

export async function summarizeArticleAction(
  articleId: string,
  length: SummaryLength
): Promise<AiActionResult<SummarizeResponse>> {
  try {
    const data = await summarizeArticle(articleId, length);
    return { ok: true, data };
  } catch (error) {
    return { ok: false, error: toErrorMessage(error) };
  }
}

export async function explainArticleAction(
  articleId: string,
  knowledgeLevel: KnowledgeLevel
): Promise<AiActionResult<ExplainResponse>> {
  try {
    const data = await explainArticle(articleId, knowledgeLevel);
    return { ok: true, data };
  } catch (error) {
    return { ok: false, error: toErrorMessage(error) };
  }
}

export async function analyzeSentimentAction(
  articleId: string
): Promise<AiActionResult<SentimentResponse>> {
  try {
    const data = await analyzeSentiment(articleId);
    return { ok: true, data };
  } catch (error) {
    return { ok: false, error: toErrorMessage(error) };
  }
}

export async function askArticleAction(
  articleId: string,
  question: string
): Promise<AiActionResult<QaResponse>> {
  const trimmed = question.trim();
  if (!trimmed) {
    return { ok: false, error: "Enter a question about this article." };
  }

  try {
    const data = await askArticle(articleId, trimmed);
    return { ok: true, data };
  } catch (error) {
    return { ok: false, error: toErrorMessage(error) };
  }
}
