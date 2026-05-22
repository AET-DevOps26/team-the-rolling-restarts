"use client";

import { toast } from "sonner";

import { AppearanceSection } from "@/components/settings/appearance-section";
import { FeedPrefsSection } from "@/components/settings/feed-prefs-section";
import { NotificationsSection } from "@/components/settings/notifications-section";
import { ProfileSection } from "@/components/settings/profile-section";
import { SettingsSectionNav } from "@/components/settings/settings-section-nav";
import { SourcesSection } from "@/components/settings/sources-section";
import { TopicsSection } from "@/components/settings/topics-section";
import { Button } from "@/components/ui/button";

export default function SettingsPage() {
  return (
    <main className="flex flex-col gap-6">
      <div className="flex flex-col gap-1">
        <h1 className="text-2xl font-semibold tracking-tight">Settings</h1>
        <p className="text-sm text-muted-foreground">
          Manage your profile, feed and notifications.
        </p>
      </div>
      <div className="grid gap-8 lg:grid-cols-[14rem_minmax(0,1fr)]">
        <SettingsSectionNav />
        <div className="flex flex-col gap-6 pb-24">
          <ProfileSection />
          <TopicsSection />
          <SourcesSection />
          <FeedPrefsSection />
          <NotificationsSection />
          <AppearanceSection />
          <div className="sticky bottom-0 flex items-center justify-end gap-2 border-t border-border bg-background/80 py-3 backdrop-blur">
            <Button variant="ghost">Discard</Button>
            <Button onClick={() => toast.success("Settings saved")}>
              Save changes
            </Button>
          </div>
        </div>
      </div>
    </main>
  );
}
