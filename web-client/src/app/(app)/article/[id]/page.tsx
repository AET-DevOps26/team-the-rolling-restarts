import { Bookmark, ChevronLeft, Share2 } from "lucide-react";
import Link from "next/link";
import { notFound } from "next/navigation";

import { RelatedArticles } from "@/components/article/related-articles";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Separator } from "@/components/ui/separator";
import {
  ARTICLES,
  dateLabel,
  getArticle,
  getSource,
  getTopic,
} from "@/lib/mock";
import { ROUTES } from "@/lib/routes";

export default async function ArticleDetailPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;
  const article = getArticle(id);

  if (!article) notFound();

  const source = getSource(article.sourceId);
  const topic = getTopic(article.topicId);
  const related = ARTICLES.filter(
    (a) => a.id !== article.id && a.topicId === article.topicId
  ).slice(0, 2);

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
      <h1 className="text-3xl font-bold tracking-tight sm:text-4xl">
        {article.headline}
      </h1>
      <div className="flex flex-wrap items-center gap-3 text-sm text-muted-foreground">
        <Avatar className="size-6">
          <AvatarFallback className="text-[10px]">
            {source?.initials ?? "??"}
          </AvatarFallback>
        </Avatar>
        <span>{source?.name ?? "Source"}</span>
        <span aria-hidden>·</span>
        <span>{article.author}</span>
        <span aria-hidden>·</span>
        <time dateTime={article.publishedAt}>{dateLabel(article.publishedAt)}</time>
        <span aria-hidden>·</span>
        <span>{article.readingMinutes} min read</span>
        <div className="ml-auto flex gap-1">
          <Button size="icon-sm" variant="ghost" aria-label="Save article">
            <Bookmark className="size-4" />
          </Button>
          <Button size="icon-sm" variant="ghost" aria-label="Share article">
            <Share2 className="size-4" />
          </Button>
        </div>
      </div>
      <div
        aria-hidden
        className="h-64 w-full rounded-lg"
        style={{ background: article.imageColor }}
      />
      <div className="flex flex-col gap-4 text-base leading-relaxed">
        {article.body.map((paragraph, i) => (
          <p key={i}>{paragraph}</p>
        ))}
      </div>
      <Separator className="my-4" />
      <RelatedArticles articles={related} />
    </main>
  );
}
