"use client";

import { AlertCircle } from "lucide-react";

import { cn } from "@/lib/utils";

export function AiInlineAlert({
  message,
  className,
}: {
  message: string;
  className?: string;
}) {
  return (
    <div
      role="alert"
      className={cn(
        "flex items-start gap-2 rounded-lg border border-destructive/30 bg-destructive/5 px-3 py-2 text-sm text-destructive",
        className
      )}
    >
      <AlertCircle className="mt-0.5 shrink-0" aria-hidden />
      <p>{message}</p>
    </div>
  );
}
