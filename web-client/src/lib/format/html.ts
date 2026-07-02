const IMG_SRC = /<img[^>]+src=["']([^"']+)["']/i;
const HTML_TAGS = /<[^>]+>/g;

export function extractImageUrl(html: string | null | undefined): string | undefined {
  if (!html) return undefined;
  return html.match(IMG_SRC)?.[1];
}

export function stripHtml(html: string | null | undefined): string {
  if (!html) return "";
  return html.replace(HTML_TAGS, " ").replace(/\s+/g, " ").trim();
}

/** Prefer backend imageUrl; fall back to parsing legacy HTML snippets. */
export function articleImageUrl(article: {
  imageUrl?: string | null;
  snippet?: string | null;
}): string | undefined {
  return article.imageUrl ?? extractImageUrl(article.snippet);
}

/** Prefer plain-text snippet; strip HTML for articles fetched before imageUrl support. */
export function articlePlainSnippet(article: { snippet?: string | null }): string {
  const snippet = article.snippet ?? "";
  return snippet.includes("<") ? stripHtml(snippet) : snippet;
}
