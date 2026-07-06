import { describe, expect, it } from "vitest";

import { articleImageUrl, articlePlainSnippet, extractImageUrl, stripHtml } from "./html";

describe("stripHtml", () => {
  it("removes tags and collapses whitespace", () => {
    expect(stripHtml('<p>Hello <strong>world</strong></p>')).toBe("Hello world");
  });

  it("decodes common HTML entities", () => {
    expect(stripHtml("Tom &amp; Jerry &quot;classic&quot;")).toBe('Tom & Jerry "classic"');
  });
});

describe("extractImageUrl", () => {
  it("reads the first img src", () => {
    const html = '<img src="https://example.com/a.jpg" /><p>Text</p>';
    expect(extractImageUrl(html)).toBe("https://example.com/a.jpg");
  });
});

describe("articlePlainSnippet", () => {
  it("strips legacy HTML snippets", () => {
    expect(
      articlePlainSnippet({ snippet: '<img src="x" /><p>Plain text here</p>' })
    ).toBe("Plain text here");
  });

  it("returns plain snippets unchanged", () => {
    expect(articlePlainSnippet({ snippet: "Already plain" })).toBe("Already plain");
  });
});

describe("articleImageUrl", () => {
  it("prefers imageUrl over snippet parsing", () => {
    expect(
      articleImageUrl({
        imageUrl: "https://cdn.example.com/hero.jpg",
        snippet: '<img src="https://other.example.com/old.jpg" />',
      })
    ).toBe("https://cdn.example.com/hero.jpg");
  });
});
