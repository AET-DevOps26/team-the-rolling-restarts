"use client";

import { useState } from "react";

import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import {
  ToggleGroup,
  ToggleGroupItem,
} from "@/components/ui/toggle-group";
import { MOCK_USER, TOPICS } from "@/lib/mock";

export function TopicsSection() {
  const [selected, setSelected] = useState<string[]>(
    MOCK_USER.selectedTopicIds
  );

  return (
    <section id="topics" className="scroll-mt-20">
      <Card>
        <CardHeader>
          <CardTitle>Topics</CardTitle>
          <CardDescription>
            Pick the topics you want in your feed.
          </CardDescription>
        </CardHeader>
        <CardContent className="flex flex-col gap-3">
          <ToggleGroup
            multiple
            value={selected}
            onValueChange={setSelected}
            variant="outline"
            className="flex flex-wrap justify-start gap-2"
          >
            {TOPICS.map((topic) => (
              <ToggleGroupItem
                key={topic.id}
                value={topic.id}
                aria-label={topic.name}
              >
                <span
                  aria-hidden
                  className="mr-2 inline-block size-2 rounded-full"
                  style={{ background: topic.color }}
                />
                {topic.name}
              </ToggleGroupItem>
            ))}
          </ToggleGroup>
          <p className="text-xs text-muted-foreground">
            Add at least 3 topics for a balanced feed.
          </p>
        </CardContent>
      </Card>
    </section>
  );
}
