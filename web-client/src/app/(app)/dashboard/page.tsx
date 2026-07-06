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

  const [articlesBundle, topics, sources, settings] = await Promise.all([
    q
      ? getArticlesPage({
          ...articleParams,
          size: FEED_PAGE_SIZE,
          q,
          page: currentPage - 1,
        })
      : getArticles({ ...articleParams, size: FEED_PAGE_SIZE }).then((articles) => ({
          articles,
          page: 0,
          size: FEED_PAGE_SIZE,
          totalElements: articles.length,
          totalPages: 1,
        })),
    getTopics(),
    getSources(),
    getMySettings(),
  ]);

  const { articles, totalElements, totalPages, size: pageSize } = articlesBundle;
  const searchPagination =
    q && totalElements > pageSize
      ? { page: currentPage, totalElements, totalPages, pageSize }
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
