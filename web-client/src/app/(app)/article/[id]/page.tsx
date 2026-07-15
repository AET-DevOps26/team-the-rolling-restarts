import { ChevronLeft, Share2 } from "lucide-react";
import Link from "next/link";
import { notFound } from "next/navigation";

import { ArticleAiPanel } from "@/components/article/ai/article-ai-panel";
import { RelatedArticles } from "@/components/article/related-articles";
import { SaveButton } from "@/components/feed/save-button";
import { ArticleThumbnailFromArticle } from "@/components/feed/article-thumbnail";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Separator } from "@/components/ui/separator";
import {
  getArticle,
  getArticles,
  getMySettings,
  getSources,
  getTopics,
} from "@/lib/api/reads";
import { articlePlainSnippet } from "@/lib/format/html";
import { dateLabel } from "@/lib/format/time";
import { ROUTES } from "@/lib/routes";

export default async function ArticleDetailPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;
  const article = await getArticle(id);
  if (!article) notFound();

  const [sources, topics, settings, relatedArticles] = await Promise.all([
    getSources(),
    getTopics(),
    getMySettings(),
    getArticles({ topicId: article.topicId, size: 6 }),
  ]);

  const sourcesById = new Map(sources.map((s) => [s.id, s]));
  const topicsById = new Map(topics.map((t) => [t.id, t]));
  const source = sourcesById.get(article.sourceId);
  const topic = topicsById.get(article.topicId);
  const related = relatedArticles.filter((a) => a.id !== article.id).slice(0, 2);
  const saved = settings.savedArticleIds.includes(article.id);

  return (
    <main className="mx-auto flex w-full max-w-2xl flex-col gap-6 py-2">
      <Link
        href={ROUTES.dashboard}
        className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground"
      >
        <ChevronLeft className="size-4" aria-hidden />
        Back to feed
      </Link>
      {topic && (
        <Badge variant="secondary" className="self-start">
          {topic.name}
        </Badge>
      )}
      <h1 className="text-3xl font-bold tracking-tight sm:text-4xl">{article.headline}</h1>
      <div className="flex flex-wrap items-center gap-3 text-sm text-muted-foreground">
        <Avatar className="size-6">
          <AvatarFallback className="text-[10px]">{source?.initials ?? "??"}</AvatarFallback>
        </Avatar>
        <span>{source?.name ?? "Source"}</span>
        <span aria-hidden>·</span>
        <span>{article.author}</span>
        <span aria-hidden>·</span>
        <time dateTime={article.publishedAt}>{dateLabel(article.publishedAt)}</time>
        <span aria-hidden>·</span>
        <span>{article.readingMinutes} min read</span>
        <div className="ml-auto flex gap-1">
          <SaveButton articleId={article.id} saved={saved} />
          <Button size="icon-sm" variant="ghost" aria-label="Share article">
            <Share2 className="size-4" />
          </Button>
        </div>
      </div>
      <div className="h-64 w-full overflow-hidden rounded-lg">
        <ArticleThumbnailFromArticle article={article} className="h-full w-full" />
      </div>
      <div className="flex flex-col gap-4 text-base leading-relaxed">
        {article.body.length > 0 ? (
          article.body.map((paragraph, i) => <p key={i}>{paragraph}</p>)
        ) : (
          <p className="text-muted-foreground">
            {articlePlainSnippet(article)}{" "}
            {article.externalUrl && (
              <a
                href={article.externalUrl}
                target="_blank"
                rel="noopener noreferrer"
                className="text-primary underline-offset-4 hover:underline"
              >
                Read the full story →
              </a>
            )}
          </p>
        )}
      </div>
      <ArticleAiPanel articleId={article.id} />
      <Separator className="my-4" />
      <RelatedArticles articles={related} sourcesById={sourcesById} topicsById={topicsById} />
    </main>
  );
}
