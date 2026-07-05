import { DashboardFeed } from "@/components/feed/dashboard-feed";
import { FeedSourcesSheet } from "@/components/feed/feed-sources-sheet";
import { getAllArticles, getArticles, getMySettings, getSources, getTopics } from "@/lib/api/reads";

export default async function DashboardPage({
  searchParams,
}: {
  searchParams: Promise<{ topic?: string; source?: string; q?: string }>;
}) {
  const { topic, source, q } = await searchParams;
  const articleParams = { topicId: topic, sourceId: source, sort: "publishedAt,desc" as const };
  const [articles, topics, sources, settings] = await Promise.all([
    q
      ? getAllArticles(articleParams)
      : getArticles({ ...articleParams, size: 50 }),
    getTopics(),
    getSources(),
    getMySettings(),
  ]);

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
      />
    </main>
  );
}
