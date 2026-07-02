import { articleImageUrl } from "@/lib/format/html";
import { colorFromId } from "@/lib/format/color";
import { cn } from "@/lib/utils";

export function ArticleThumbnail({
  sourceId,
  imageUrl,
  alt,
  className,
}: {
  sourceId: string;
  imageUrl?: string | null;
  alt: string;
  className?: string;
}) {
  const resolved = imageUrl ?? undefined;

  if (resolved) {
    return (
      // eslint-disable-next-line @next/next/no-img-element -- RSS thumbnails come from many external domains.
      <img
        src={resolved}
        alt={alt}
        className={cn("size-full object-cover", className)}
        loading="lazy"
      />
    );
  }

  return (
    <div
      aria-hidden
      className={cn("size-full", className)}
      style={{ background: colorFromId(sourceId) }}
    />
  );
}

export function ArticleThumbnailFromArticle({
  article,
  className,
}: {
  article: { id: string; sourceId: string; imageUrl?: string | null; snippet?: string | null; headline: string };
  className?: string;
}) {
  return (
    <ArticleThumbnail
      sourceId={article.sourceId}
      imageUrl={articleImageUrl(article)}
      alt={article.headline}
      className={className}
    />
  );
}
