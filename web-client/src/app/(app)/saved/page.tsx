import { ArticleCard } from "@/components/feed/article-card";
import { Empty, EmptyDescription, EmptyTitle } from "@/components/ui/empty";
import { getMySettings, getSavedArticles, getSources, getTopics } from "@/lib/api/reads";

export default async function SavedPage() {
  const settings = await getMySettings();
  const [articles, sources, topics] = await Promise.all([
    getSavedArticles(settings.savedArticleIds),
    getSources(),
    getTopics(),
  ]);
  const sourcesById = new Map(sources.map((s) => [s.id, s]));
  const topicsById = new Map(topics.map((t) => [t.id, t]));

  return (
    <main className="flex flex-col gap-6">
      <div className="flex flex-col gap-1">
        <h1 className="text-2xl font-semibold tracking-tight">Saved</h1>
        <p className="text-sm text-muted-foreground">
          {articles.length} saved stor{articles.length === 1 ? "y" : "ies"}
        </p>
      </div>
      {articles.length === 0 ? (
        <Empty>
          <EmptyTitle>Nothing saved yet</EmptyTitle>
          <EmptyDescription>Bookmark articles from your feed to read them later.</EmptyDescription>
        </Empty>
      ) : (
        <div className="flex flex-col gap-4">
          {articles.map((article) => (
            <ArticleCard
              key={article.id}
              article={article}
              source={sourcesById.get(article.sourceId)}
              topic={topicsById.get(article.topicId)}
              saved
            />
          ))}
        </div>
      )}
    </main>
  );
}
