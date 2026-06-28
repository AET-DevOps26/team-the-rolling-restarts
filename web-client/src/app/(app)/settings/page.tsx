import { AppearanceSection } from "@/components/settings/appearance-section";
import { FeedPrefsSection } from "@/components/settings/feed-prefs-section";
import { NotificationsSection } from "@/components/settings/notifications-section";
import { ProfileSection } from "@/components/settings/profile-section";
import { SettingsSectionNav } from "@/components/settings/settings-section-nav";
import { SourcesSection } from "@/components/settings/sources-section";
import { TopicsSection } from "@/components/settings/topics-section";
import { getMe, getMySettings, getSources, getTopics } from "@/lib/api/reads";

export default async function SettingsPage() {
  const [user, settings, topics, sources] = await Promise.all([
    getMe(),
    getMySettings(),
    getTopics(),
    getSources(),
  ]);

  return (
    <main className="flex flex-col gap-6">
      <div className="flex flex-col gap-1">
        <h1 className="text-2xl font-semibold tracking-tight">Settings</h1>
        <p className="text-sm text-muted-foreground">Manage your profile, feed and notifications.</p>
      </div>
      <div className="grid gap-8 lg:grid-cols-[14rem_minmax(0,1fr)]">
        <SettingsSectionNav />
        <div className="flex flex-col gap-6 pb-24">
          <ProfileSection user={user} />
          <TopicsSection topics={topics} selectedTopicIds={settings.selectedTopicIds} />
          <SourcesSection sources={sources} enabledSourceIds={settings.enabledSourceIds} />
          <FeedPrefsSection />
          <NotificationsSection />
          <AppearanceSection />
        </div>
      </div>
    </main>
  );
}
