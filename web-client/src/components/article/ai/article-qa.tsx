"use client";

import { Loader2 } from "lucide-react";
import { useState, useTransition } from "react";

import { askArticleAction } from "@/app/(app)/article/[id]/ai-actions";
import { AiInlineAlert } from "@/components/article/ai/ai-inline-alert";
import { AiLoadingPlaceholder } from "@/components/article/ai/ai-loading-placeholder";
import { Button } from "@/components/ui/button";
import { Field, FieldContent, FieldDescription, FieldLabel } from "@/components/ui/field";
import { Textarea } from "@/components/ui/textarea";
import type { QaResponse } from "@/lib/api/ai";

export function ArticleQa({ articleId }: { articleId: string }) {
  const [question, setQuestion] = useState("");
  const [result, setResult] = useState<QaResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isPending, startTransition] = useTransition();

  function handleAsk() {
    setError(null);
    startTransition(async () => {
      const response = await askArticleAction(articleId, question);
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
      <Field>
        <FieldLabel htmlFor="article-question">Your question</FieldLabel>
        <FieldContent>
          <Textarea
            id="article-question"
            value={question}
            onChange={(event) => setQuestion(event.target.value)}
            placeholder="What would you like to know about this article?"
            disabled={isPending}
            rows={3}
          />
          <FieldDescription>Answers are grounded in this article&apos;s text.</FieldDescription>
        </FieldContent>
      </Field>

      <Button type="button" disabled={isPending || question.trim().length === 0} onClick={handleAsk}>
        {isPending ? (
          <>
            <Loader2 className="animate-spin" data-icon="inline-start" aria-hidden />
            Asking…
          </>
        ) : (
          "Ask"
        )}
      </Button>

      {error && <AiInlineAlert message={error} />}
      {isPending && <AiLoadingPlaceholder />}
      {result && !isPending && (
        <div aria-live="polite" className="flex flex-col gap-2 rounded-lg bg-muted/40 p-4">
          <p className="leading-relaxed whitespace-pre-wrap">{result.answer}</p>
          <p className="text-xs text-muted-foreground">
            {result.provider} · {result.model}
          </p>
        </div>
      )}
    </div>
  );
}
