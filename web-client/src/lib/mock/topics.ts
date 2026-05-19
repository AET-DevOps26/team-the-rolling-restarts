import type { Topic } from "./types";

export const TOPICS: Topic[] = [
  { id: "technology", name: "Technology", color: "#6366f1" },
  { id: "business", name: "Business", color: "#0ea5e9" },
  { id: "politics", name: "Politics", color: "#ef4444" },
  { id: "science", name: "Science", color: "#22c55e" },
  { id: "health", name: "Health", color: "#14b8a6" },
  { id: "climate", name: "Climate", color: "#84cc16" },
  { id: "sports", name: "Sports", color: "#f97316" },
  { id: "culture", name: "Culture", color: "#a855f7" },
  { id: "world", name: "World", color: "#64748b" },
  { id: "finance", name: "Finance", color: "#eab308" },
];

export const TOPICS_BY_ID = new Map(TOPICS.map((t) => [t.id, t]));
export const getTopic = (id: string): Topic | undefined => TOPICS_BY_ID.get(id);
