import type { ReactNode } from "react";

import { AppSidebar } from "@/components/layout/app-sidebar";
import { AppTopbar } from "@/components/layout/app-topbar";
import { Toaster } from "@/components/ui/sonner";

export default function AppLayout({ children }: { children: ReactNode }) {
  return (
    <div className="flex min-h-screen flex-1">
      <AppSidebar className="hidden md:flex" />
      <div className="flex min-h-screen flex-1 flex-col">
        <AppTopbar />
        <div className="mx-auto w-full max-w-5xl flex-1 px-4 py-6">
          {children}
        </div>
      </div>
      <Toaster richColors />
    </div>
  );
}
