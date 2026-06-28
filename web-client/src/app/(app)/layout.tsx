import { redirect } from "next/navigation";
import type { ReactNode } from "react";

import { AppSidebar } from "@/components/layout/app-sidebar";
import { AppTopbar } from "@/components/layout/app-topbar";
import { Toaster } from "@/components/ui/sonner";
import { ApiError } from "@/lib/api/client";
import { getMe, getSources, getTopics } from "@/lib/api/reads";

async function loadShell() {
  try {
    const [user, topics, sources] = await Promise.all([getMe(), getTopics(), getSources()]);
    return { user, topics, sources };
  } catch (e) {
    // An expired/invalid JWT yields a 401 even though the cookie is still
    // present (middleware only checks presence). Send the user back to log in.
    if (e instanceof ApiError && e.status === 401) redirect("/login");
    throw e;
  }
}

export default async function AppLayout({ children }: { children: ReactNode }) {
  const { user, topics, sources } = await loadShell();

  return (
    <div className="flex min-h-screen flex-1">
      <AppSidebar className="hidden md:flex" topics={topics} sources={sources} />
      <div className="flex min-h-screen flex-1 flex-col">
        <AppTopbar avatarInitials={user.avatarInitials} topics={topics} sources={sources} />
        <div className="mx-auto w-full max-w-5xl flex-1 px-4 py-6">{children}</div>
      </div>
      <Toaster richColors />
    </div>
  );
}
