import { Button } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";

export default function Home() {
  return (
    <main className="mx-auto flex min-h-screen max-w-3xl flex-col items-center justify-center gap-8 p-8">
      <h1 className="text-4xl font-bold tracking-tight">
        Personalised News Aggregator
      </h1>
      <p className="text-muted-foreground">
        Web client scaffolding. Feed and personalisation coming soon.
      </p>

      <Card className="w-full">
        <CardHeader>
          <CardTitle>Status</CardTitle>
          <CardDescription>
            Next.js + Tailwind + shadcn/ui wired up.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <Button disabled>Sign in (coming soon)</Button>
        </CardContent>
      </Card>
    </main>
  );
}
