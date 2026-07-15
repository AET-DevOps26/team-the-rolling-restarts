"use client";

import { Loader2 } from "lucide-react";
import { useState, useTransition } from "react";

import { analyzeSentimentAction } from "@/app/(app)/article/[id]/ai-actions";
import { AiInlineAlert } from "@/components/article/ai/ai-inline-alert";
import { AiLoadingPlaceholder } from "@/components/article/ai/ai-loading-placeholder";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import type { SentimentResponse } from "@/lib/api/ai";

function sentimentVariant(
  sentiment: SentimentResponse["sentiment"]
): "default" | "secondary" | "destructive" {
  if (sentiment === "positive") return "default";
  if (sentiment === "negative") return "destructive";
  return "secondary";
}

export function ArticleSentiment({ articleId }: { articleId: string }) {
  const [result, setResult] = useState<SentimentResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isPending, startTransition] = useTransition();

  function handleAnalyze() {
    setError(null);
    startTransition(async () => {
      const response = await analyzeSentimentAction(articleId);
      if (response.ok) {
        setResult(response.data);
      } else {
        setResult(null);
        setError(response.error);
      }
    });
  }

  return (
    <div className="flex flex-col gap-4">
      <Button type="button" disabled={isPending} onClick={handleAnalyze}>
        {isPending ? (
          <>
            <Loader2 className="animate-spin" data-icon="inline-start" aria-hidden />
            Analyzing…
          </>
        ) : (
          "Analyze sentiment"
        )}
      </Button>

      {error && <AiInlineAlert message={error} />}
      {isPending && <AiLoadingPlaceholder />}
      {result && !isPending && (
        <div aria-live="polite" className="flex flex-col gap-3 rounded-lg bg-muted/40 p-4">
          <div className="flex flex-wrap items-center gap-2">
            <Badge variant={sentimentVariant(result.sentiment)}>{result.sentiment}</Badge>
            <span className="text-sm text-muted-foreground">Score: {result.score.toFixed(2)}</span>
            {result.bias && (
              <Badge variant="outline">Bias: {result.bias}</Badge>
            )}
          </div>
          <p className="leading-relaxed">{result.rationale}</p>
          <p className="text-xs text-muted-foreground">
            {result.provider} · {result.model}
          </p>
        </div>
      )}
    </div>
  );
}
