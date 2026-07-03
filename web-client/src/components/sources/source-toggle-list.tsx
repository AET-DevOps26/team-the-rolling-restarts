"use client";

import { AlertCircle, Loader2 } from "lucide-react";
import { useState } from "react";
import { toast } from "sonner";

import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { Badge } from "@/components/ui/badge";
import { Switch } from "@/components/ui/switch";
import { subscribe, unsubscribe } from "@/lib/actions/content";
import type { Source } from "@/lib/api/types";

export function SourceToggleList({
  sources,
  enabledSourceIds,
}: {
  sources: Source[];
  enabledSourceIds: string[];
}) {
  const [enabled, setEnabled] = useState<Set<string>>(() => new Set(enabledSourceIds));
  const [pending, setPending] = useState(false);

  async function toggle(sourceId: string, next: boolean) {
    setEnabled((current) => {
      const updated = new Set(current);
      if (next) updated.add(sourceId);
      else updated.delete(sourceId);
      return updated;
    });
    setPending(true);
    try {
      const res = next ? await subscribe(sourceId) : await unsubscribe(sourceId);
      if (!res.ok) {
        setEnabled((current) => {
          const reverted = new Set(current);
          if (next) reverted.delete(sourceId);
          else reverted.add(sourceId);
          return reverted;
        });
        toast.error(res.error);
      }
    } finally {
      setPending(false);
    }
  }

  return (
    <ul className="flex flex-col divide-y divide-border">
      {sources.map((source) => (
        <li key={source.id} className="flex items-center gap-4 py-3 first:pt-0 last:pb-0">
          <Avatar className="size-9">
            <AvatarFallback>{source.initials}</AvatarFallback>
          </Avatar>
          <div className="min-w-0 flex-1">
            <div className="flex items-center gap-2">
              <p className="truncate text-sm font-medium">{source.name}</p>
              <SourceStatusBadge source={source} />
            </div>
            <p className="text-xs text-muted-foreground">
              {source.subscriberCount} subscriber{source.subscriberCount === 1 ? "" : "s"}
            </p>
            {source.fetchStatus === "FAILED" && source.fetchError && (
              <p className="mt-0.5 text-xs text-destructive">{source.fetchError}</p>
            )}
          </div>
          <Switch
            checked={enabled.has(source.id)}
            disabled={pending}
            onCheckedChange={(next) => toggle(source.id, next)}
            aria-label={`Toggle ${source.name}`}
          />
        </li>
      ))}
    </ul>
  );
}

function SourceStatusBadge({ source }: { source: Source }) {
  if (source.fetchStatus === "PENDING") {
    return (
      <Badge variant="secondary" className="gap-1">
        <Loader2 className="size-3 animate-spin" aria-hidden />
        Fetching
      </Badge>
    );
  }
  if (source.fetchStatus === "FAILED") {
    return (
      <Badge variant="destructive" className="gap-1">
        <AlertCircle className="size-3" aria-hidden />
        Fetch failed
      </Badge>
    );
  }
  return null;
}
