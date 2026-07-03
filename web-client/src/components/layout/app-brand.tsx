import Link from "next/link";

import { ROUTES } from "@/lib/routes";

export function AppBrand({ href = ROUTES.dashboard }: { href?: string }) {
  return (
    <Link
      href={href}
      className="flex items-center gap-2 rounded-md outline-none focus-visible:ring-3 focus-visible:ring-ring/50"
    >
      <span aria-hidden className="inline-block size-6 rounded-md bg-primary" />
      <span className="font-semibold tracking-tight">NewsLens</span>
    </Link>
  );
}
