"use client";

import { Loader2 } from "lucide-react";
import { useState, useTransition } from "react";

import { explainArticleAction } from "@/app/(app)/article/[id]/ai-actions";
import { AiInlineAlert } from "@/components/article/ai/ai-inline-alert";
import { AiLoadingPlaceholder } from "@/components/article/ai/ai-loading-placeholder";
import { Button } from "@/components/ui/button";
import { Field, FieldContent, FieldDescription, FieldLabel } from "@/components/ui/field";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import type { ExplainResponse, KnowledgeLevel } from "@/lib/api/ai";

const KNOWLEDGE_LEVELS: { value: KnowledgeLevel; label: string }[] = [
  { value: "child", label: "Child" },
  { value: "beginner", label: "Beginner" },
  { value: "intermediate", label: "Intermediate" },
  { value: "expert", label: "Expert" },
];

export function ArticleExplain({ articleId }: { articleId: string }) {
  const [knowledgeLevel, setKnowledgeLevel] = useState<KnowledgeLevel>("beginner");
  const [result, setResult] = useState<ExplainResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isPending, startTransition] = useTransition();

  function handleExplain() {
    setError(null);
    startTransition(async () => {
      const response = await explainArticleAction(articleId, knowledgeLevel);
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
        <FieldLabel htmlFor="knowledge-level">Knowledge level</FieldLabel>
        <FieldContent>
          <Select
            value={knowledgeLevel}
            onValueChange={(value) => {
              if (!isPending) setKnowledgeLevel(value as KnowledgeLevel);
            }}
            disabled={isPending}
          >
            <SelectTrigger id="knowledge-level" className="w-full max-w-xs" aria-label="Knowledge level">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {KNOWLEDGE_LEVELS.map((option) => (
                <SelectItem key={option.value} value={option.value}>
                  {option.label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          <FieldDescription>Tailor the explanation to the reader&apos;s background.</FieldDescription>
        </FieldContent>
      </Field>

      <Button type="button" disabled={isPending} onClick={handleExplain}>
        {isPending ? (
          <>
            <Loader2 className="animate-spin" data-icon="inline-start" aria-hidden />
            Explaining…
          </>
        ) : (
          "Explain simply"
        )}
      </Button>

      {error && <AiInlineAlert message={error} />}
      {isPending && <AiLoadingPlaceholder />}
      {result && !isPending && (
        <div aria-live="polite" className="flex flex-col gap-2 rounded-lg bg-muted/40 p-4">
          <p className="leading-relaxed whitespace-pre-wrap">{result.explanation}</p>
          <p className="text-xs text-muted-foreground">
            {result.provider} · {result.model} · {result.knowledgeLevel}
          </p>
        </div>
      )}
    </div>
  );
}
