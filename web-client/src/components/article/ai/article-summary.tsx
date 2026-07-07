"use client";

import { Loader2 } from "lucide-react";
import { useState, useTransition } from "react";

import { summarizeArticleAction } from "@/app/(app)/article/[id]/ai-actions";
import { AiInlineAlert } from "@/components/article/ai/ai-inline-alert";
import { AiLoadingPlaceholder } from "@/components/article/ai/ai-loading-placeholder";
import { Button } from "@/components/ui/button";
import { Field, FieldContent, FieldDescription, FieldLabel } from "@/components/ui/field";
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group";
import type { SummarizeResponse, SummaryLength } from "@/lib/api/ai";

const LENGTH_OPTIONS: { value: SummaryLength; label: string }[] = [
  { value: "short", label: "Short" },
  { value: "medium", label: "Medium" },
  { value: "long", label: "Long" },
];

export function ArticleSummary({ articleId }: { articleId: string }) {
  const [length, setLength] = useState<SummaryLength>("short");
  const [result, setResult] = useState<SummarizeResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isPending, startTransition] = useTransition();

  function handleSummarize() {
    setError(null);
    startTransition(async () => {
      const response = await summarizeArticleAction(articleId, length);
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
        <FieldLabel htmlFor="summary-length">Summary length</FieldLabel>
        <FieldContent>
          <ToggleGroup
            id="summary-length"
            value={[length]}
            onValueChange={(values) => {
              const next = values[0] as SummaryLength | undefined;
              if (next && !isPending) setLength(next);
            }}
            variant="outline"
            disabled={isPending}
            aria-label="Summary length"
          >
            {LENGTH_OPTIONS.map((option) => (
              <ToggleGroupItem key={option.value} value={option.value} aria-label={option.label}>
                {option.label}
              </ToggleGroupItem>
            ))}
          </ToggleGroup>
          <FieldDescription>Choose how detailed the summary should be.</FieldDescription>
        </FieldContent>
      </Field>

      <Button type="button" disabled={isPending} onClick={handleSummarize}>
        {isPending ? (
          <>
            <Loader2 className="animate-spin" data-icon="inline-start" aria-hidden />
            Summarizing…
          </>
        ) : (
          "Summarize"
        )}
      </Button>

      {error && <AiInlineAlert message={error} />}
      {isPending && <AiLoadingPlaceholder />}
      {result && !isPending && (
        <div aria-live="polite" className="flex flex-col gap-2 rounded-lg bg-muted/40 p-4">
          <p className="leading-relaxed whitespace-pre-wrap">{result.summary}</p>
          <p className="text-xs text-muted-foreground">
            {result.provider} · {result.model}
          </p>
        </div>
      )}
    </div>
  );
}
