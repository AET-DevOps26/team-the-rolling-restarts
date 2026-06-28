import { Share2 } from "lucide-react";
import Link from "next/link";

import { SaveButton } from "@/components/feed/save-button";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import type { Article, Source, Topic } from "@/lib/api/types";
import { colorFromId } from "@/lib/format/color";
import { relativeTime } from "@/lib/format/time";
import { articleHref } from "@/lib/routes";

export function ArticleCard({
  article,
  source,
  topic,
  saved = false,
}: {
  article: Article;
  source?: Source;
  topic?: Topic;
  saved?: boolean;
}) {
  return (
    <article className="flex gap-4 rounded-lg border border-border bg-card p-4 transition hover:shadow-sm">
      <div
        aria-hidden
        className="hidden size-24 shrink-0 rounded-md sm:block sm:size-32"
        style={{ background: colorFromId(article.sourceId) }}
      />
      <div className="flex min-w-0 flex-1 flex-col gap-2">
        <div className="flex items-center gap-2 text-xs text-muted-foreground">
          <Avatar className="size-5">
            <AvatarFallback className="text-[10px]">{source?.initials ?? "??"}</AvatarFallback>
          </Avatar>
          <span>{source?.name ?? "Unknown source"}</span>
          <span aria-hidden>·</span>
          <time dateTime={article.publishedAt}>{relativeTime(article.publishedAt)}</time>
        </div>
        <Link
          href={articleHref(article.id)}
          className="line-clamp-2 text-base font-semibold tracking-tight hover:underline"
        >
          {article.headline}
        </Link>
        <p className="line-clamp-2 text-sm text-muted-foreground">{article.snippet}</p>
        <div className="mt-1 flex items-center gap-3 text-xs">
          {topic && <Badge variant="secondary">{topic.name}</Badge>}
          <span className="text-muted-foreground">{article.readingMinutes} min read</span>
          <div className="ml-auto flex gap-1">
            <SaveButton articleId={article.id} saved={saved} />
            <Button size="icon-sm" variant="ghost" aria-label="Share article">
              <Share2 className="size-4" />
            </Button>
          </div>
        </div>
      </div>
    </article>
  );
}
