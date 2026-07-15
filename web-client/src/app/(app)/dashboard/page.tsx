import { DashboardFeed } from "@/components/feed/dashboard-feed";
import { FeedSourcesSheet } from "@/components/feed/feed-sources-sheet";
import { getArticles, getArticlesPage, getMySettings, getSources, getTopics } from "@/lib/api/reads";

const FEED_PAGE_SIZE = 50;

function parseSearchPage(raw: string | undefined): number {
  const n = Number(raw ?? "1");
  if (!Number.isFinite(n) || n < 1) return 1;
  return Math.floor(n);
}

export default async function DashboardPage({
  searchParams,
}: {
  searchParams: Promise<{ topic?: string; source?: string; q?: string; page?: string }>;
}) {
  const { topic, source, q, page: pageParam } = await searchParams;
  const currentPage = parseSearchPage(pageParam);
  const articleParams = { topicId: topic, sourceId: source, sort: "publishedAt,desc" as const };

  const [topics, sources, settings] = await Promise.all([getTopics(), getSources(), getMySettings()]);

  // A user subscribed to zero sources always has zero matching articles; skip the network call
  // rather than sending an empty `sourceIds` filter, which is indistinguishable on the wire from
  // "no filter at all" (a repeated query param with zero occurrences vs. the param being absent).
  const articlesBundle =
    settings.enabledSourceIds.length === 0
      ? { articles: [], page: 0, size: FEED_PAGE_SIZE, totalElements: 0, totalPages: 0 }
      : q
        ? await getArticlesPage({
            ...articleParams,
            sourceIds: settings.enabledSourceIds,
            size: FEED_PAGE_SIZE,
            q,
            page: currentPage - 1,
          })
        : await getArticles({
            ...articleParams,
            sourceIds: settings.enabledSourceIds,
            size: FEED_PAGE_SIZE,
          }).then((articles) => ({
            articles,
            page: 0,
            size: FEED_PAGE_SIZE,
            totalElements: articles.length,
            totalPages: 1,
          }));

  const { articles, totalElements, totalPages, size: pageSize } = articlesBundle;
  const boundedPage =
    totalPages > 0 ? Math.min(currentPage, totalPages) : 1;
  const searchPagination =
    q && totalElements > pageSize
      ? { page: boundedPage, totalElements, totalPages, pageSize }
      : undefined;

  return (
    <main className="flex flex-col gap-6">
      <div className="flex items-end justify-between gap-4">
        <div className="flex flex-col gap-1">
          <h1 className="text-2xl font-semibold tracking-tight">Your feed</h1>
          <p className="text-sm text-muted-foreground">Stories tailored to your interests.</p>
        </div>
        <FeedSourcesSheet sources={sources} enabledSourceIds={settings.enabledSourceIds} />
      </div>
      <DashboardFeed
        articles={articles}
        topics={topics}
        sources={sources}
        savedIds={settings.savedArticleIds}
        selectedTopicIds={settings.selectedTopicIds}
        enabledSourceIds={settings.enabledSourceIds}
        topic={topic}
        source={source}
        query={q}
        searchPagination={searchPagination}
      />
    </main>
  );
}
