import Link from "next/link";

import { buttonVariants } from "@/components/ui/button";
import { ROUTES } from "@/lib/routes";
import { cn } from "@/lib/utils";

export function MarketingHeader() {
  return (
    <header className="sticky top-0 z-30 border-b border-border bg-background/80 backdrop-blur">
      <div className="mx-auto flex h-14 w-full max-w-6xl items-center justify-between px-4">
        <Link href={ROUTES.home} className="flex items-center gap-2">
          <span
            aria-hidden
            className="inline-block size-6 rounded-md bg-primary"
          />
          <span className="font-semibold tracking-tight">NewsLens</span>
        </Link>
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
