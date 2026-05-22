import Link from "next/link";
import type { ReactNode } from "react";

import { ROUTES } from "@/lib/routes";

export default function AuthLayout({ children }: { children: ReactNode }) {
  return (
    <div className="flex flex-1 flex-col items-center justify-center bg-muted/30 px-4 py-12">
      <Link
        href={ROUTES.home}
        className="mb-8 flex items-center gap-2"
        aria-label="NewsLens home"
      >
        <span aria-hidden className="inline-block size-7 rounded-md bg-primary" />
        <span className="text-lg font-semibold tracking-tight">NewsLens</span>
      </Link>
      {children}
      <p className="mt-6 text-xs text-muted-foreground">
        Need help?{" "}
        <Link href="#" className="underline-offset-4 hover:underline">
          Contact support
        </Link>
      </p>
    </div>
  );
}
