"use client";

import { useState } from "react";

import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Switch } from "@/components/ui/switch";
import { MOCK_USER, SOURCES } from "@/lib/mock";

export function SourcesSection() {
  const [enabled, setEnabled] = useState<Set<string>>(
    () => new Set(MOCK_USER.enabledSourceIds)
  );

  function toggle(sourceId: string, next: boolean) {
    setEnabled((current) => {
      const updated = new Set(current);
      if (next) updated.add(sourceId);
      else updated.delete(sourceId);
      return updated;
    });
  }

  return (
    <section id="sources" className="scroll-mt-20">
      <Card>
        <CardHeader>
          <CardTitle>Sources</CardTitle>
          <CardDescription>
            Subscribe to the publications you trust; mute the rest.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <ul className="flex flex-col divide-y divide-border">
            {SOURCES.map((source) => (
              <li
                key={source.id}
                className="flex items-center gap-4 py-3 first:pt-0 last:pb-0"
              >
                <Avatar className="size-9">
                  <AvatarFallback>{source.initials}</AvatarFallback>
                </Avatar>
                <div className="min-w-0 flex-1">
                  <p className="text-sm font-medium">{source.name}</p>
                  <p className="text-xs text-muted-foreground">
                    {source.weeklyCount} articles this week
                  </p>
                </div>
                <Switch
                  checked={enabled.has(source.id)}
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
