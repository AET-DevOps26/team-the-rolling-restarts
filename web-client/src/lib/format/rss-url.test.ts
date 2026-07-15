import { describe, expect, it } from "vitest";

import { normalizeRssUrl } from "./rss-url";

describe("normalizeRssUrl", () => {
  it("prepends https when scheme is omitted", () => {
    expect(normalizeRssUrl("rss.example.com/feed")).toBe("https://rss.example.com/feed");
  });

  it("leaves http and https URLs unchanged", () => {
    expect(normalizeRssUrl("https://example.com/feed")).toBe("https://example.com/feed");
    expect(normalizeRssUrl("http://example.com/feed")).toBe("http://example.com/feed");
  });

  it("trims whitespace", () => {
    expect(normalizeRssUrl("  example.com/feed  ")).toBe("https://example.com/feed");
  });
});
