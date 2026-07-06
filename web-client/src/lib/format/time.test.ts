import { describe, expect, it, vi, afterEach } from "vitest";
import { relativeTime, dateLabel } from "./time";

afterEach(() => vi.useRealTimers());

describe("relativeTime", () => {
  it("formats minutes/hours/days from now", () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date("2026-05-14T08:00:00.000Z"));
    expect(relativeTime(new Date("2026-05-14T07:30:00.000Z").toISOString())).toBe("30m ago");
    expect(relativeTime(new Date("2026-05-14T05:00:00.000Z").toISOString())).toBe("3h ago");
    expect(relativeTime(new Date("2026-05-11T08:00:00.000Z").toISOString())).toBe("3d ago");
  });

  it("does not round partial minutes up", () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date("2026-05-14T08:00:30.000Z"));
    expect(relativeTime(new Date("2026-05-14T08:00:00.000Z").toISOString())).toBe("just now");
  });
});

describe("dateLabel", () => {
  it("formats a UTC date", () => {
    expect(dateLabel("2026-05-14T08:00:00.000Z")).toBe("14 May 2026");
  });
});
