import Link from "next/link";

import { dashboardHref, type ActiveFilters } from "@/components/feed/feed-toolbar";
import { buttonVariants } from "@/components/ui/button";
import { cn } from "@/lib/utils";

export function FeedSearchPagination({
  filters,
  page,
  totalElements,
  totalPages,
  pageSize,
}: {
  filters: ActiveFilters;
  /** 1-based current page. */
  page: number;
  totalElements: number;
  totalPages: number;
  pageSize: number;
}) {
  const start = (page - 1) * pageSize + 1;
  const end = Math.min(page * pageSize, totalElements);

  return (
    <nav
      aria-label="Search results pages"
      className="flex flex-col gap-3 border-t border-border pt-4 sm:flex-row sm:items-center sm:justify-between"
    >
      <p className="text-sm text-muted-foreground" role="status">
        Showing {start.toLocaleString()}–{end.toLocaleString()} of {totalElements.toLocaleString()}{" "}
        results
      </p>
      <div className="flex items-center gap-2">
        {page > 1 ? (
          <Link
            href={dashboardHref({ ...filters, page: page - 1 })}
            className={cn(buttonVariants({ variant: "outline", size: "sm" }))}
          >
            Previous
          </Link>
        ) : (
          <span
            className={cn(
              buttonVariants({ variant: "outline", size: "sm" }),
              "pointer-events-none opacity-50"
            )}
            aria-hidden
          >
            Previous
          </span>
        )}
        <span className="px-1 text-sm text-muted-foreground">
          Page {page} of {totalPages}
        </span>
        {page < totalPages ? (
          <Link
            href={dashboardHref({ ...filters, page: page + 1 })}
            className={cn(buttonVariants({ variant: "outline", size: "sm" }))}
          >
            Next
          </Link>
        ) : (
          <span
            className={cn(
              buttonVariants({ variant: "outline", size: "sm" }),
              "pointer-events-none opacity-50"
            )}
            aria-hidden
          >
            Next
          </span>
        )}
      </div>
    </nav>
  );
}
