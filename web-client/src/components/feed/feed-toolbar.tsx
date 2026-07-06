"use client";

import { X } from "lucide-react";
import Link from "next/link";

import { Badge } from "@/components/ui/badge";
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group";
import type { Source, Topic } from "@/lib/api/types";

export type FeedSort = "for-you" | "trending" | "latest";

const SORTS: readonly FeedSort[] = ["for-you", "trending", "latest"];

function isFeedSort(value: string): value is FeedSort {
  return (SORTS as readonly string[]).includes(value);
}

export type ActiveFilters = {
  topic?: string;
  source?: string;
  q?: string;
  /** 1-based page for search results; omitted on page 1. */
  page?: number;
};

export function dashboardHref(filters: ActiveFilters) {
  const params = new URLSearchParams();
  if (filters.topic) params.set("topic", filters.topic);
  if (filters.source) params.set("source", filters.source);
  if (filters.q) params.set("q", filters.q);
  if (filters.page && filters.page > 1) params.set("page", String(filters.page));
  const search = params.toString();
  return search ? `/dashboard?${search}` : "/dashboard";
}

function chipHrefWithoutKey(key: keyof ActiveFilters, filters: ActiveFilters) {
  const next = { ...filters };
  delete next[key];
  if (key === "q") delete next.page;
  return dashboardHref(next);
}

export function FeedToolbar({
  sort,
  onSortChange,
  filters,
  topicsById,
  sourcesById,
}: {
  sort: FeedSort;
  onSortChange: (next: FeedSort) => void;
  filters: ActiveFilters;
  topicsById: Map<string, Topic>;
  sourcesById: Map<string, Source>;
}) {
  const topic = filters.topic ? topicsById.get(filters.topic) : undefined;
  const source = filters.source ? sourcesById.get(filters.source) : undefined;
  const hasFilters = Boolean(filters.topic || filters.source);

  return (
    <div className="flex flex-wrap items-center justify-between gap-3">
      <ToggleGroup
        value={[sort]}
        onValueChange={(values: string[]) => {
          const next = values[0];
          if (next && isFeedSort(next)) onSortChange(next);
        }}
        variant="outline"
        size="sm"
        aria-label="Sort"
      >
        <ToggleGroupItem value="for-you">For you</ToggleGroupItem>
        <ToggleGroupItem value="trending">Trending</ToggleGroupItem>
        <ToggleGroupItem value="latest">Latest</ToggleGroupItem>
      </ToggleGroup>
      {hasFilters && (
        <div className="flex flex-wrap items-center gap-2">
          {topic && (
            <Badge variant="secondary" className="gap-1.5">
              {topic.name}
              <Link
                href={chipHrefWithoutKey("topic", filters)}
                aria-label={`Remove topic filter ${topic.name}`}
                className="inline-flex"
              >
                <X className="size-3" />
              </Link>
            </Badge>
          )}
          {!topic && filters.topic && (
            <Badge variant="secondary" className="gap-1.5">
              {filters.topic}
              <Link
                href={chipHrefWithoutKey("topic", filters)}
                aria-label={`Remove topic filter ${filters.topic}`}
                className="inline-flex"
              >
                <X className="size-3" />
              </Link>
            </Badge>
          )}
          {source && (
            <Badge variant="secondary" className="gap-1.5">
              {source.name}
              <Link
                href={chipHrefWithoutKey("source", filters)}
                aria-label={`Remove source filter ${source.name}`}
                className="inline-flex"
              >
                <X className="size-3" />
              </Link>
            </Badge>
          )}
          {!source && filters.source && (
            <Badge variant="secondary" className="gap-1.5">
              {filters.source}
              <Link
                href={chipHrefWithoutKey("source", filters)}
                aria-label={`Remove source filter ${filters.source}`}
                className="inline-flex"
              >
                <X className="size-3" />
              </Link>
            </Badge>
          )}
        </div>
      )}
    </div>
  );
}
