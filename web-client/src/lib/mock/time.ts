const REFERENCE_NOW = new Date("2026-05-14T08:00:00.000Z").getTime();

export function hoursAgoIso(hours: number): string {
  return new Date(REFERENCE_NOW - hours * 60 * 60 * 1000).toISOString();
}

export function relativeTime(iso: string): string {
  const then = new Date(iso).getTime();
  const diffMinutes = Math.max(0, Math.round((REFERENCE_NOW - then) / 60000));
  if (diffMinutes < 1) return "just now";
  if (diffMinutes < 60) return `${diffMinutes}m ago`;
  const diffHours = Math.round(diffMinutes / 60);
  if (diffHours < 24) return `${diffHours}h ago`;
  if (diffHours < 48) return "Yesterday";
  const diffDays = Math.round(diffHours / 24);
  return `${diffDays}d ago`;
}

export function dateLabel(iso: string): string {
  const d = new Date(iso);
  return d.toLocaleDateString("en-GB", {
    day: "numeric",
    month: "short",
    year: "numeric",
    timeZone: "UTC",
  });
}
