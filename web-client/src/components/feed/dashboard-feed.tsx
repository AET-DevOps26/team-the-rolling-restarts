"use client";

import { useMemo, useState } from "react";

import { ArticleCard } from "@/components/feed/article-card";
import { EmptyFeed } from "@/components/feed/empty-feed";
import { FeedSearchPagination } from "@/components/feed/feed-search-pagination";
import { FeedToolbar, type FeedSort } from "@/components/feed/feed-toolbar";
import type { Article, Source, Topic } from "@/lib/api/types";

function applySort(
  list: Article[],
  sort: FeedSort,
  selectedTopicIds: string[]
): Article[] {
  const sorted = [...list];
  switch (sort) {
    case "latest":
      return sorted.sort((a, b) => Date.parse(b.publishedAt) - Date.parse(a.publishedAt));
    case "trending":
      return sorted.sort((a, b) => b.readingMinutes - a.readingMinutes);
    case "for-you":
    default:
      return sorted.sort((a, b) => {
        const aSel = selectedTopicIds.includes(a.topicId) ? 0 : 1;
        const bSel = selectedTopicIds.includes(b.topicId) ? 0 : 1;
        return aSel - bSel || Date.parse(b.publishedAt) - Date.parse(a.publishedAt);
      });
  }
}

export function DashboardFeed({
  articles,
  topics,
  sources,
  savedIds,
  selectedTopicIds,
  enabledSourceIds,
  topic,
  source,
  query,
  searchPagination,
}: {
  articles: Article[];
  topics: Topic[];
  sources: Source[];
  savedIds: string[];
  selectedTopicIds: string[];
  enabledSourceIds: string[];
  topic?: string;
  source?: string;
  query?: string;
  searchPagination?: {
    page: number;
    totalElements: number;
    totalPages: number;
    pageSize: number;
  };
}) {
  const [sort, setSort] = useState<FeedSort>("for-you");

  const topicsById = useMemo(() => new Map(topics.map((t) => [t.id, t])), [topics]);
  const sourcesById = useMemo(() => new Map(sources.map((s) => [s.id, s])), [sources]);
  const savedSet = useMemo(() => new Set(savedIds), [savedIds]);
  const enabledSources = useMemo(() => new Set(enabledSourceIds), [enabledSourceIds]);

  const visible = useMemo(() => {
    const list = articles.filter((a) => enabledSources.has(a.sourceId));
    return applySort(list, sort, selectedTopicIds);
  }, [articles, enabledSources, sort, selectedTopicIds]);

  const topicName = topic ? topicsById.get(topic)?.name ?? topic : undefined;
  const sourceName = source ? sourcesById.get(source)?.name ?? source : undefined;
  const filterMessage = [
    query ? `search "${query}"` : null,
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
        filters={{ topic, source, q: query }}
        topicsById={topicsById}
        sourcesById={sourcesById}
      />
      {visible.length === 0 ? (
        <EmptyFeed
          message={
            filterMessage
              ? `No matches for ${filterMessage}.`
              : enabledSourceIds.length === 0
                ? "Subscribe to sources in Settings to build your feed."
                : "No articles available yet."
          }
        />
      ) : (
        <div className="flex flex-col gap-4">
          {visible.map((article) => (
            <ArticleCard
              key={article.id}
              article={article}
              source={sourcesById.get(article.sourceId)}
              topic={topicsById.get(article.topicId)}
              saved={savedSet.has(article.id)}
            />
          ))}
        </div>
      )}
      {searchPagination ? (
        <FeedSearchPagination
          filters={{ topic, source, q: query }}
          page={searchPagination.page}
          totalElements={searchPagination.totalElements}
          totalPages={searchPagination.totalPages}
          pageSize={searchPagination.pageSize}
        />
      ) : null}
    </>
  );
}
