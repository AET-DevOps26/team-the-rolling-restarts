import Link from "next/link";

import { FeatureGrid } from "@/components/marketing/feature-grid";
import { Hero } from "@/components/marketing/hero";
import { HowItWorks } from "@/components/marketing/how-it-works";
import { MarketingFooter } from "@/components/marketing/marketing-footer";
import { MarketingHeader } from "@/components/marketing/marketing-header";
import { SourcesStrip } from "@/components/marketing/sources-strip";
import { buttonVariants } from "@/components/ui/button";
import { ROUTES } from "@/lib/routes";
import { cn } from "@/lib/utils";

export default function Home() {
  return (
    <>
      <MarketingHeader />
      <main className="flex flex-1 flex-col">
        <Hero />
        <SourcesStrip />
        <FeatureGrid />
        <HowItWorks />
        <section className="border-t border-border bg-primary/5">
          <div className="mx-auto flex w-full max-w-4xl flex-col items-center gap-4 px-4 py-16 text-center">
            <h2 className="text-2xl font-semibold tracking-tight sm:text-3xl">
              Ready to read smarter?
            </h2>
            <p className="text-muted-foreground">
              Set up your feed in under a minute.
            </p>
            <Link
              href={ROUTES.signup}
              className={cn(buttonVariants({ size: "lg" }))}
            >
              Get started
            </Link>
          </div>
        </section>
        <MarketingFooter />
      </main>
    </>
  );
}
