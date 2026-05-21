import type { ReactNode } from "react";

import { Card } from "@/components/ui/card";

export function AuthShellCard({ children }: { children: ReactNode }) {
  return <Card className="w-full max-w-md">{children}</Card>;
}
