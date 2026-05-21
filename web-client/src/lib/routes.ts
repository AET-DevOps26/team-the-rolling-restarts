export const ROUTES = {
  home: "/",
  login: "/login",
  signup: "/signup",
  forgotPassword: "/forgot-password",
  dashboard: "/dashboard",
  saved: "/saved",
  settings: "/settings",
} as const;

export type RouteHref = (typeof ROUTES)[keyof typeof ROUTES];

export const articleHref = (id: string) => `/article/${id}` as const;

export type MainNavIcon = "home" | "bookmark" | "settings";

export const mainNav: { href: RouteHref; label: string; icon: MainNavIcon }[] = [
  { href: ROUTES.dashboard, label: "Feed", icon: "home" },
  { href: ROUTES.saved, label: "Saved", icon: "bookmark" },
  { href: ROUTES.settings, label: "Settings", icon: "settings" },
];
