"use client";

import { useMemo, useState } from "react";

import { ArticleCard } from "@/components/feed/article-card";
import { EmptyFeed } from "@/components/feed/empty-feed";
import {
  FeedToolbar,
  type FeedSort,
} from "@/components/feed/feed-toolbar";
import { ARTICLES, getSource, getTopic } from "@/lib/mock";

function sortAndFilter(sort: FeedSort, topic?: string, source?: string) {
  let list = ARTICLES;
  if (topic) list = list.filter((a) => a.topicId === topic);
  if (source) list = list.filter((a) => a.sourceId === source);
  const sorted = [...list];
  switch (sort) {
    case "latest":
      return sorted.sort(
        (a, b) => Date.parse(b.publishedAt) - Date.parse(a.publishedAt)
      );
    case "trending":
      return sorted.sort((a, b) => a.id.localeCompare(b.id));
    case "for-you":
    default:
      return sorted;
  }
}

export function DashboardFeed({
  topic,
  source,
}: {
  topic?: string;
  source?: string;
}) {
  const [sort, setSort] = useState<FeedSort>("for-you");
  const articles = useMemo(() => sortAndFilter(sort, topic, source), [sort, topic, source]);
  const topicName = topic ? (getTopic(topic)?.name ?? topic) : undefined;
  const sourceName = source ? (getSource(source)?.name ?? source) : undefined;

  const filterMessage = [
    topicName ? `topic "${topicName}"` : null,
    sourceName ? `source "${sourceName}"` : null,
  ]
    .filter(Boolean)
    .join(" and ");

  return (
    <>
      <FeedToolbar
        sort={sort}
        onSortChange={setSort}
        filters={{ topic, source }}
      />
      {articles.length === 0 ? (
        <EmptyFeed
          message={
            filterMessage
              ? `No matches for ${filterMessage}.`
              : "No articles available."
          }
        />
      ) : (
        <div className="flex flex-col gap-4">
          {articles.map((article) => (
            <ArticleCard key={article.id} article={article} />
          ))}
        </div>
      )}
      <div className="flex items-center justify-center pt-4">
        <button
          type="button"
          disabled
          className="rounded-md border border-border px-4 py-2 text-sm text-muted-foreground opacity-60"
        >
          Load more
        </button>
      </div>
    </>
  );
}
