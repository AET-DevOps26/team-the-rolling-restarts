import type { MockUser } from "./types";

export const MOCK_USER: MockUser = {
  name: "Alex Rivera",
  email: "alex@example.com",
  avatarInitials: "AR",
  selectedTopicIds: ["technology", "science", "climate", "world"],
  enabledSourceIds: [
    "reuters",
    "bbc",
    "the-verge",
    "bloomberg",
    "the-guardian",
    "wired",
  ],
  savedArticleIds: ["a-2", "a-5", "a-9", "a-13"],
};
