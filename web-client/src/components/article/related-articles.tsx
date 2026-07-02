import Link from "next/link";

import { Badge } from "@/components/ui/badge";
import { ArticleThumbnailFromArticle } from "@/components/feed/article-thumbnail";
import type { Article, Source, Topic } from "@/lib/api/types";
import { articleHref } from "@/lib/routes";

export function RelatedArticles({
  articles,
  sourcesById,
  topicsById,
}: {
  articles: Article[];
  sourcesById: Map<string, Source>;
  topicsById: Map<string, Topic>;
}) {
  if (articles.length === 0) return null;

  return (
    <section aria-label="Related articles" className="flex flex-col gap-4">
      <h2 className="text-lg font-semibold tracking-tight">Related</h2>
      <ul className="grid gap-4 sm:grid-cols-2">
        {articles.map((article) => {
          const source = sourcesById.get(article.sourceId);
          const topic = topicsById.get(article.topicId);
          return (
            <li key={article.id}>
              <Link
                href={articleHref(article.id)}
                className="flex h-full flex-col gap-2 rounded-lg border border-border bg-card p-4 transition hover:shadow-sm"
              >
                <div className="h-24 overflow-hidden rounded-md">
                  <ArticleThumbnailFromArticle article={article} className="h-full w-full" />
                </div>
                <div className="flex items-center gap-2 text-xs text-muted-foreground">
                  <span>{source?.name ?? "Source"}</span>
                  <span aria-hidden>·</span>
                  <span>{article.readingMinutes} min read</span>
                </div>
                <p className="line-clamp-2 text-sm font-semibold">{article.headline}</p>
                {topic && (
                  <Badge variant="secondary" className="self-start">
                    {topic.name}
                  </Badge>
                )}
              </Link>
            </li>
          );
        })}
      </ul>
    </section>
  );
}
