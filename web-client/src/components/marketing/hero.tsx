import Link from "next/link";

import { Badge } from "@/components/ui/badge";
import { buttonVariants } from "@/components/ui/button";
import { ROUTES } from "@/lib/routes";
import { cn } from "@/lib/utils";

export function Hero() {
  return (
    <section className="mx-auto flex w-full max-w-3xl flex-col items-center gap-6 px-4 py-20 text-center">
      <Badge variant="secondary" className="rounded-full">
        New · Personalised feeds
      </Badge>
      <h1 className="text-balance text-4xl font-bold tracking-tight sm:text-5xl">
        Your news, your way.
      </h1>
      <p className="text-balance text-base text-muted-foreground sm:text-lg">
        A personalised aggregator that filters thousands of sources down to the
        stories you actually care about.
      </p>
      <div className="flex flex-wrap items-center justify-center gap-3">
        <Link
          href={ROUTES.signup}
          className={cn(buttonVariants({ size: "lg" }))}
        >
          Get started
        </Link>
        <Link
          href={ROUTES.login}
          className={cn(buttonVariants({ variant: "ghost", size: "lg" }))}
        >
          Sign in
        </Link>
      </div>
    </section>
  );
}
