import { ApiError } from "@/lib/api/client";

export type ApiErrorDisplay = { message: string; details: string[] };

export function toApiErrorDisplay(e: unknown, fallback: string): ApiErrorDisplay {
  if (e instanceof ApiError) {
    return { message: e.message, details: e.details };
  }
  return { message: fallback, details: [] };
}
