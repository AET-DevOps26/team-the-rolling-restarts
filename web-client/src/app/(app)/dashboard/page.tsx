import { DashboardFeed } from "@/components/feed/dashboard-feed";

export default async function DashboardPage({
  searchParams,
}: {
  searchParams: Promise<{ topic?: string; source?: string }>;
}) {
  const { topic, source } = await searchParams;
  return (
    <main className="flex flex-col gap-6">
      <div className="flex items-end justify-between gap-4">
        <div className="flex flex-col gap-1">
          <h1 className="text-2xl font-semibold tracking-tight">Your feed</h1>
          <p className="text-sm text-muted-foreground">
            Stories tailored to your interests.
          </p>
        </div>
      </div>
      <DashboardFeed topic={topic} source={source} />
    </main>
  );
}
