import { describe, expect, it } from "vitest";
import { colorFromId } from "./color";

describe("colorFromId", () => {
  it("is deterministic for the same id", () => {
    expect(colorFromId("reuters")).toBe(colorFromId("reuters"));
  });
  it("returns an hsl string", () => {
    expect(colorFromId("bbc")).toMatch(/^hsl\(\d+ \d+% \d+%\)$/);
  });
  it("differs for different ids", () => {
    expect(colorFromId("a")).not.toBe(colorFromId("b"));
  });
});
