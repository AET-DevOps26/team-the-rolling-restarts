import { ArticleCard } from "@/components/feed/article-card";
import { Empty, EmptyDescription, EmptyTitle } from "@/components/ui/empty";
import { ARTICLES, MOCK_USER } from "@/lib/mock";

export default function SavedPage() {
  const savedSet = new Set(MOCK_USER.savedArticleIds);
  const articles = ARTICLES.filter((a) => savedSet.has(a.id));

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
          <EmptyDescription>
            Bookmark articles from your feed to read them later.
          </EmptyDescription>
        </Empty>
      ) : (
        <div className="flex flex-col gap-4">
          {articles.map((article) => (
            <ArticleCard key={article.id} article={article} />
          ))}
        </div>
      )}
    </main>
  );
}
