"use client";

import { Bookmark } from "lucide-react";
import { useState, useTransition } from "react";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import { saveArticle, unsaveArticle } from "@/lib/actions/content";

export function SaveButton({ articleId, saved }: { articleId: string; saved: boolean }) {
  const [isSaved, setIsSaved] = useState(saved);
  const [pending, startTransition] = useTransition();

  function toggle() {
    const next = !isSaved;
    setIsSaved(next);
    startTransition(async () => {
      const res = next ? await saveArticle(articleId) : await unsaveArticle(articleId);
      if (!res.ok) {
        setIsSaved(!next);
        toast.error(res.error);
      } else {
        toast.success(next ? "Saved" : "Removed from saved");
      }
    });
  }

  return (
    <Button
      size="icon-sm"
      variant="ghost"
      aria-label={isSaved ? "Remove from saved" : "Save article"}
      aria-pressed={isSaved}
      disabled={pending}
      onClick={toggle}
    >
      <Bookmark className={isSaved ? "size-4 fill-current" : "size-4"} />
    </Button>
  );
}
