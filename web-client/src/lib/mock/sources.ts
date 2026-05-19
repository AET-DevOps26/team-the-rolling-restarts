import type { Source } from "./types";

export const SOURCES: Source[] = [
  { id: "reuters", name: "Reuters", initials: "RT", weeklyCount: 58 },
  { id: "bbc", name: "BBC", initials: "BB", weeklyCount: 49 },
  { id: "the-verge", name: "The Verge", initials: "TV", weeklyCount: 32 },
  { id: "bloomberg", name: "Bloomberg", initials: "BL", weeklyCount: 41 },
  { id: "techcrunch", name: "TechCrunch", initials: "TC", weeklyCount: 27 },
  { id: "the-guardian", name: "The Guardian", initials: "GU", weeklyCount: 38 },
  { id: "ars-technica", name: "Ars Technica", initials: "AT", weeklyCount: 18 },
  { id: "mit-tech-review", name: "MIT Tech Review", initials: "MT", weeklyCount: 11 },
  { id: "ap", name: "Associated Press", initials: "AP", weeklyCount: 44 },
  { id: "wired", name: "Wired", initials: "WI", weeklyCount: 22 },
];

export const SOURCES_BY_ID = new Map(SOURCES.map((s) => [s.id, s]));
export const getSource = (id: string): Source | undefined => SOURCES_BY_ID.get(id);
