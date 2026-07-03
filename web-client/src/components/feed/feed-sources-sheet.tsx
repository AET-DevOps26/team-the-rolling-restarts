"use client";

import Link from "next/link";

import { SourceToggleList } from "@/components/sources/source-toggle-list";
import { Button } from "@/components/ui/button";
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
  SheetTrigger,
} from "@/components/ui/sheet";
import type { Source } from "@/lib/api/types";
import { ROUTES } from "@/lib/routes";

export function FeedSourcesSheet({
  sources,
  enabledSourceIds,
}: {
  sources: Source[];
  enabledSourceIds: string[];
}) {
  return (
    <Sheet>
      <SheetTrigger render={<Button variant="outline" size="sm" />}>
        Sources
        {enabledSourceIds.length > 0 && (
          <span className="text-muted-foreground">({enabledSourceIds.length})</span>
        )}
      </SheetTrigger>
      <SheetContent side="right" className="w-full sm:max-w-md">
        <SheetHeader>
          <SheetTitle>Your sources</SheetTitle>
          <SheetDescription>
            Choose which RSS feeds appear in your feed.{" "}
            <Link href={`${ROUTES.settings}#sources`} className="text-primary underline-offset-4 hover:underline">
              Add new feeds in Settings
            </Link>
            .
          </SheetDescription>
        </SheetHeader>
        <div className="flex-1 overflow-y-auto px-4 pb-4">
          {sources.length === 0 ? (
            <p className="text-sm text-muted-foreground">
              No sources available yet.{" "}
              <Link href={`${ROUTES.settings}#sources`} className="text-primary underline-offset-4 hover:underline">
                Add your first feed in Settings
              </Link>
              .
            </p>
          ) : (
            <SourceToggleList
              key={enabledSourceIds.join(",")}
              sources={sources}
              enabledSourceIds={enabledSourceIds}
            />
          )}
        </div>
      </SheetContent>
    </Sheet>
  );
}
