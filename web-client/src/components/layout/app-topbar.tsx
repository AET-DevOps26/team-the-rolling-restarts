"use client";

import { Bell } from "lucide-react";
import { usePathname, useRouter } from "next/navigation";

import { AppBrand } from "@/components/layout/app-brand";
import { AppMobileNav } from "@/components/layout/app-mobile-nav";
import { TopbarSearch } from "@/components/layout/topbar-search";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { logout } from "@/lib/actions/auth";
import type { Source, Topic } from "@/lib/api/types";
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

export function AppTopbar({
  avatarInitials,
  topics,
  sources,
}: {
  avatarInitials: string;
  topics: Topic[];
  sources: Source[];
}) {
  const router = useRouter();
  const pathname = usePathname();
  const title = titleFromPathname(pathname);

  return (
    <header className="sticky top-0 z-30 flex h-14 items-center gap-3 border-b border-border bg-background/80 px-4 backdrop-blur">
      <div className="md:hidden">
        <AppMobileNav topics={topics} sources={sources} />
      </div>
      <div className="md:hidden">
        <AppBrand />
      </div>
      <h1 className="hidden text-sm font-medium md:block md:text-base">{title}</h1>
      <TopbarSearch />
      <Button size="icon-sm" variant="ghost" aria-label="Notifications" className="ml-auto md:ml-0">
        <Bell className="size-5" />
      </Button>
      <DropdownMenu>
        <DropdownMenuTrigger
          aria-label="Open user menu"
          className="inline-flex items-center rounded-full outline-none focus-visible:ring-3 focus-visible:ring-ring/50"
        >
          <Avatar className="size-8">
            <AvatarFallback>{avatarInitials}</AvatarFallback>
          </Avatar>
        </DropdownMenuTrigger>
        <DropdownMenuContent align="end">
          <DropdownMenuItem onClick={() => router.push(ROUTES.settings)}>Profile</DropdownMenuItem>
          <DropdownMenuItem onClick={() => router.push(ROUTES.settings)}>Settings</DropdownMenuItem>
          <DropdownMenuSeparator />
          <DropdownMenuItem onClick={() => logout()}>Sign out</DropdownMenuItem>
        </DropdownMenuContent>
      </DropdownMenu>
    </header>
  );
}
