"use client";

import { Bell, Search } from "lucide-react";
import { usePathname, useRouter } from "next/navigation";

import { AppMobileNav } from "@/components/layout/app-mobile-nav";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Input } from "@/components/ui/input";
import { MOCK_USER } from "@/lib/mock";
import { ROUTES } from "@/lib/routes";

const TITLES: Record<string, string> = {
  "/dashboard": "Feed",
  "/saved": "Saved",
  "/settings": "Settings",
};

function titleFromPathname(pathname: string | null): string {
  if (!pathname) return "";
  if (pathname.startsWith("/article/")) return "Article";
  return TITLES[pathname] ?? "";
}

export function AppTopbar() {
  const router = useRouter();
  const pathname = usePathname();
  const title = titleFromPathname(pathname);

  return (
    <header className="sticky top-0 z-30 flex h-14 items-center gap-3 border-b border-border bg-background/80 px-4 backdrop-blur">
      <div className="md:hidden">
        <AppMobileNav />
      </div>
      <h1 className="text-sm font-medium md:text-base">{title}</h1>
      <div className="relative ml-auto hidden w-72 md:block">
        <Search
          aria-hidden
          className="pointer-events-none absolute inset-y-0 left-2.5 my-auto size-4 text-muted-foreground"
        />
        <Input
          aria-label="Search articles"
          placeholder="Search articles…"
          className="pl-8"
        />
      </div>
      <Button
        size="icon-sm"
        variant="ghost"
        aria-label="Notifications"
        className="ml-auto md:ml-0"
      >
        <Bell className="size-5" />
      </Button>
      <DropdownMenu>
        <DropdownMenuTrigger
          aria-label="Open user menu"
          className="inline-flex items-center rounded-full outline-none focus-visible:ring-3 focus-visible:ring-ring/50"
        >
          <Avatar className="size-8">
            <AvatarFallback>{MOCK_USER.avatarInitials}</AvatarFallback>
          </Avatar>
        </DropdownMenuTrigger>
        <DropdownMenuContent align="end">
          <DropdownMenuItem onClick={() => router.push(ROUTES.settings)}>
            Profile
          </DropdownMenuItem>
          <DropdownMenuItem onClick={() => router.push(ROUTES.settings)}>
            Settings
          </DropdownMenuItem>
          <DropdownMenuSeparator />
          <DropdownMenuItem onClick={() => router.push(ROUTES.home)}>
            Sign out
          </DropdownMenuItem>
        </DropdownMenuContent>
      </DropdownMenu>
    </header>
  );
}
