"use client";

import { Bookmark, Home, Settings } from "lucide-react";
import Link from "next/link";
import { usePathname } from "next/navigation";

import { Badge } from "@/components/ui/badge";
import { Separator } from "@/components/ui/separator";
import { SOURCES, TOPICS } from "@/lib/mock";
import {
  mainNav,
  type MainNavIcon,
  type RouteHref,
} from "@/lib/routes";
import { cn } from "@/lib/utils";

const ICONS: Record<MainNavIcon, typeof Home> = {
  home: Home,
  bookmark: Bookmark,
  settings: Settings,
};

function isActive(pathname: string | null, href: RouteHref) {
  if (!pathname) return false;
  if (href === "/") return pathname === href;
  return pathname === href || pathname.startsWith(`${href}/`);
}

export function AppSidebar({ className }: { className?: string }) {
  const pathname = usePathname();
  return (
    <aside
      className={cn(
        "flex w-64 flex-col border-r border-border bg-background",
        className
      )}
    >
      <div className="flex h-14 items-center gap-2 border-b border-border px-4">
        <span
          aria-hidden
          className="inline-block size-6 rounded-md bg-primary"
        />
        <span className="font-semibold tracking-tight">NewsLens</span>
      </div>
      <nav aria-label="Primary" className="flex flex-col gap-1 px-3 py-4">
        {mainNav.map((item) => {
          const Icon = ICONS[item.icon];
          const active = isActive(pathname, item.href);
          return (
            <Link
              key={item.href}
              href={item.href}
              className={cn(
                "inline-flex items-center gap-3 rounded-md px-3 py-2 text-sm font-medium transition",
                active
                  ? "bg-muted text-foreground"
                  : "text-muted-foreground hover:bg-muted hover:text-foreground"
              )}
            >
              <Icon className="size-4" aria-hidden />
              {item.label}
            </Link>
          );
        })}
      </nav>
      <Separator />
      <div className="flex flex-col gap-2 px-3 py-4">
        <p className="px-3 text-xs font-medium uppercase tracking-wider text-muted-foreground">
          Topics
        </p>
        <ul className="flex flex-col gap-0.5">
          {TOPICS.map((topic) => (
            <li key={topic.id}>
              <Link
                href={`/dashboard?topic=${topic.id}`}
                className="flex items-center justify-between rounded-md px-3 py-1.5 text-sm text-muted-foreground hover:bg-muted hover:text-foreground"
              >
                <span className="flex items-center gap-2">
                  <span
                    aria-hidden
                    className="inline-block size-2 rounded-full"
                    style={{ background: topic.color }}
                  />
                  {topic.name}
                </span>
              </Link>
            </li>
          ))}
        </ul>
      </div>
      <Separator />
      <div className="flex flex-col gap-2 px-3 py-4">
        <p className="px-3 text-xs font-medium uppercase tracking-wider text-muted-foreground">
          Sources
        </p>
        <ul className="flex flex-col gap-0.5">
          {SOURCES.slice(0, 6).map((source) => (
            <li key={source.id}>
              <Link
                href={`/dashboard?source=${source.id}`}
                className="flex items-center justify-between rounded-md px-3 py-1.5 text-sm text-muted-foreground hover:bg-muted hover:text-foreground"
              >
                <span>{source.name}</span>
                <span className="text-xs text-muted-foreground/70">
                  {source.weeklyCount}
                </span>
              </Link>
            </li>
          ))}
        </ul>
      </div>
      <div className="mt-auto px-4 py-4">
        <Badge variant="secondary" className="w-full justify-center">
          Free plan
        </Badge>
      </div>
    </aside>
  );
}
