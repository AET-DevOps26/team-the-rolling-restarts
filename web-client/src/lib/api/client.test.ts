import { afterEach, describe, expect, it, vi } from "vitest";

const cookieStore = { get: vi.fn() };
vi.mock("next/headers", () => ({ cookies: async () => cookieStore }));
vi.mock("server-only", () => ({}));

import { apiFetch } from "./client";

afterEach(() => {
  vi.restoreAllMocks();
  cookieStore.get.mockReset();
});

describe("apiFetch", () => {
  it("builds URL from base and attaches bearer token", async () => {
    process.env.NEXT_PUBLIC_API_BASE_URL = "http://gw:8080";
    cookieStore.get.mockReturnValue({ value: "jwt-123" });
    const fetchMock = vi
      .spyOn(globalThis, "fetch")
      .mockResolvedValue(new Response(JSON.stringify({ ok: true }), { status: 200 }));

    const data = await apiFetch<{ ok: boolean }>("/api/content/topics");

    expect(data).toEqual({ ok: true });
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe("http://gw:8080/api/content/topics");
    expect((init?.headers as Record<string, string>).Authorization).toBe("Bearer jwt-123");
  });

  it("omits Authorization when no cookie", async () => {
    cookieStore.get.mockReturnValue(undefined);
    const fetchMock = vi
      .spyOn(globalThis, "fetch")
      .mockResolvedValue(new Response("[]", { status: 200 }));
    await apiFetch("/api/content/topics");
    const init = fetchMock.mock.calls[0][1];
    expect((init?.headers as Record<string, string>).Authorization).toBeUndefined();
  });

  it("throws ApiError with parsed unified error on non-2xx", async () => {
    cookieStore.get.mockReturnValue(undefined);
    vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response(
        JSON.stringify({ code: 400, message: "Bad", details: ["x: y"], path: "/p" }),
        { status: 400 }
      )
    );
    await expect(apiFetch("/p")).rejects.toMatchObject({
      name: "ApiError",
      status: 400,
      message: "Bad",
      details: ["x: y"],
    });
  });
});
