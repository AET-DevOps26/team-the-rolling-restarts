export type Topic = {
  id: string;
  name: string;
  color: string;
};

export type Source = {
  id: string;
  name: string;
  initials: string;
  weeklyCount: number;
};

export type Article = {
  id: string;
  headline: string;
  snippet: string;
  body: string[];
  sourceId: Source["id"];
  topicId: Topic["id"];
  author: string;
  publishedAt: string;
  readingMinutes: number;
  imageColor: string;
};

export type MockUser = {
  name: string;
  email: string;
  avatarInitials: string;
  selectedTopicIds: string[];
  enabledSourceIds: string[];
  savedArticleIds: string[];
};
