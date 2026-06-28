import type { ReactNode } from "react";

import { AppSidebar } from "@/components/layout/app-sidebar";
import { AppTopbar } from "@/components/layout/app-topbar";
import { Toaster } from "@/components/ui/sonner";
import { getMe, getSources, getTopics } from "@/lib/api/reads";

export default async function AppLayout({ children }: { children: ReactNode }) {
  const [user, topics, sources] = await Promise.all([getMe(), getTopics(), getSources()]);

  return (
    <div className="flex min-h-screen flex-1">
      <AppSidebar className="hidden md:flex" topics={topics} sources={sources} />
      <div className="flex min-h-screen flex-1 flex-col">
        <AppTopbar avatarInitials={user.avatarInitials} />
        <div className="mx-auto w-full max-w-5xl flex-1 px-4 py-6">{children}</div>
      </div>
      <Toaster richColors />
    </div>
  );
}
