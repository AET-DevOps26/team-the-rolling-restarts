import { Mail, Shield, Sparkles } from "lucide-react";

import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";

type Feature = { icon: typeof Sparkles; title: string; description: string };

const FEATURES: Feature[] = [
  {
    icon: Sparkles,
    title: "Personalised feed",
    description:
      "Tell NewsLens what you care about and we'll surface the stories that actually matter to you.",
  },
  {
    icon: Shield,
    title: "Trusted sources",
    description:
      "Curated from established outlets so your feed is grounded in real reporting, not noise.",
  },
  {
    icon: Mail,
    title: "Daily digest",
    description:
      "Get a calm, well-edited briefing in your inbox each morning — read it in five minutes.",
  },
];

export function FeatureGrid() {
  return (
    <section className="mx-auto w-full max-w-6xl px-4 py-20">
      <div className="grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
        {FEATURES.map((feature) => {
          const Icon = feature.icon;
          return (
            <Card key={feature.title}>
              <CardHeader>
                <div className="flex size-10 items-center justify-center rounded-md bg-primary/10 text-primary">
                  <Icon className="size-5" aria-hidden />
                </div>
                <CardTitle>{feature.title}</CardTitle>
                <CardDescription>{feature.description}</CardDescription>
              </CardHeader>
              <CardContent />
            </Card>
          );
        })}
      </div>
    </section>
  );
}
