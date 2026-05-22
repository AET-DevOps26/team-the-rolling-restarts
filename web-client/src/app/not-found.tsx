import Link from "next/link";

import { buttonVariants } from "@/components/ui/button";
import { ROUTES } from "@/lib/routes";
import { cn } from "@/lib/utils";

export default function NotFound() {
  return (
    <main className="flex flex-1 flex-col items-center justify-center gap-4 px-4 py-24 text-center">
      <Link href={ROUTES.home} className="mb-4 flex items-center gap-2">
        <span
          aria-hidden
          className="inline-block size-7 rounded-md bg-primary"
        />
        <span className="text-lg font-semibold tracking-tight">NewsLens</span>
      </Link>
      <p className="text-6xl font-bold tracking-tight text-muted-foreground">
        404
      </p>
      <h1 className="text-2xl font-semibold">Page not found</h1>
      <p className="max-w-md text-muted-foreground">
        We couldn&apos;t find what you were looking for. The link may be broken
        or the page may have moved.
      </p>
      <Link href={ROUTES.home} className={cn(buttonVariants(), "mt-2")}>
        Go home
      </Link>
    </main>
  );
}
