"use client";

import { useState, useTransition } from "react";
import { toast } from "sonner";

import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Switch } from "@/components/ui/switch";
import { subscribe, unsubscribe } from "@/lib/actions/content";
import type { Source } from "@/lib/api/types";

export function SourcesSection({
  sources,
  enabledSourceIds,
}: {
  sources: Source[];
  enabledSourceIds: string[];
}) {
  const [enabled, setEnabled] = useState<Set<string>>(() => new Set(enabledSourceIds));
  const [pending, startTransition] = useTransition();

  function toggle(sourceId: string, next: boolean) {
    setEnabled((current) => {
      const updated = new Set(current);
      if (next) updated.add(sourceId);
      else updated.delete(sourceId);
      return updated;
    });
    startTransition(async () => {
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
    });
  }

  return (
    <section id="sources" className="scroll-mt-20">
      <Card>
        <CardHeader>
          <CardTitle>Sources</CardTitle>
          <CardDescription>Subscribe to the publications you trust; mute the rest.</CardDescription>
        </CardHeader>
        <CardContent>
          <ul className="flex flex-col divide-y divide-border">
            {sources.map((source) => (
              <li key={source.id} className="flex items-center gap-4 py-3 first:pt-0 last:pb-0">
                <Avatar className="size-9">
                  <AvatarFallback>{source.initials}</AvatarFallback>
                </Avatar>
                <div className="min-w-0 flex-1">
                  <p className="text-sm font-medium">{source.name}</p>
                  <p className="text-xs text-muted-foreground">
                    {source.subscriberCount} subscriber{source.subscriberCount === 1 ? "" : "s"}
                  </p>
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
        </CardContent>
      </Card>
    </section>
  );
}
