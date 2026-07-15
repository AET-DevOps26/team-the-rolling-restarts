/** Prepends https:// when the user omits a scheme (e.g. rss.example.com/feed). */
export function normalizeRssUrl(url: string): string {
  const trimmed = url.trim();
  if (!trimmed) return trimmed;
  if (/^[a-zA-Z][a-zA-Z\d+.-]*:/.test(trimmed)) return trimmed;
  return `https://${trimmed}`;
}
