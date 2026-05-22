import Link from "next/link";

import { Empty, EmptyDescription, EmptyTitle } from "@/components/ui/empty";
import { ROUTES } from "@/lib/routes";

export function EmptyFeed({ message }: { message: string }) {
  return (
    <Empty>
      <EmptyTitle>No matches</EmptyTitle>
      <EmptyDescription>{message}</EmptyDescription>
      <Link
        href={ROUTES.dashboard}
        className="text-sm text-primary underline-offset-4 hover:underline"
      >
        Clear filters
      </Link>
    </Empty>
  );
}
