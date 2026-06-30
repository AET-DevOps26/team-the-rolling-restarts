import type { components } from "@/generated/api";

type Schemas = components["schemas"];

// springdoc marks every response field optional, but the backend Java records
// always populate them. Expose ergonomic required types; callers rely on the
// backend contract (reads.ts uses compile-time assertions, not runtime validation).
export type Article = Required<Schemas["Article"]>;
export type Source = Required<Schemas["Source"]>;
export type Topic = Required<Schemas["Topic"]>;
export type UserProfile = Required<Schemas["UserProfileResponse"]>;
export type UserSettings = Required<Schemas["UserSettingsResponse"]>;
export type PageArticle = Schemas["PageArticle"];
export type TokenResponse = Schemas["TokenResponse"];
export type LoginRequest = Schemas["LoginRequest"];
export type RegisterRequest = Schemas["RegisterRequest"];
