import Link from "next/link";

import { AppBrand } from "@/components/layout/app-brand";
import { buttonVariants } from "@/components/ui/button";
import { ROUTES } from "@/lib/routes";
import { cn } from "@/lib/utils";

export function MarketingHeader() {
  return (
    <header className="sticky top-0 z-30 border-b border-border bg-background/80 backdrop-blur">
      <div className="mx-auto flex h-14 w-full max-w-6xl items-center justify-between px-4">
        <AppBrand href={ROUTES.home} />
        <nav aria-label="Marketing" className="flex items-center gap-2">
          <Link
            href={ROUTES.login}
            className={cn(buttonVariants({ variant: "ghost", size: "sm" }))}
          >
            Log in
          </Link>
          <Link
            href={ROUTES.signup}
            className={cn(buttonVariants({ size: "sm" }))}
          >
            Get started
          </Link>
        </nav>
      </div>
    </header>
  );
}
