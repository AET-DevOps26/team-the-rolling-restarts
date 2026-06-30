"use client";

import { useState } from "react";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardFooter,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group";
import { updateSelectedTopics } from "@/lib/actions/user";
import type { Topic } from "@/lib/api/types";

export function TopicsSection({
  topics,
  selectedTopicIds,
}: {
  topics: Topic[];
  selectedTopicIds: string[];
}) {
  const [selected, setSelected] = useState<string[]>(selectedTopicIds);
  const [pending, setPending] = useState(false);

  async function save() {
    setPending(true);
    try {
      const res = await updateSelectedTopics(selected);
      toast[res.ok ? "success" : "error"](res.ok ? "Topics saved" : res.error);
    } finally {
      setPending(false);
    }
  }

  return (
    <section id="topics" className="scroll-mt-20">
      <Card>
        <CardHeader>
          <CardTitle>Topics</CardTitle>
          <CardDescription>Pick the topics you want in your feed.</CardDescription>
        </CardHeader>
        <CardContent className="flex flex-col gap-3">
          <ToggleGroup
            multiple
            value={selected}
            onValueChange={setSelected}
            variant="outline"
            className="flex flex-wrap justify-start gap-2"
          >
            {topics.map((topic) => (
              <ToggleGroupItem key={topic.id} value={topic.id} aria-label={topic.name}>
                <span
                  aria-hidden
                  className="mr-2 inline-block size-2 rounded-full"
                  style={{ background: topic.color }}
                />
                {topic.name}
              </ToggleGroupItem>
            ))}
          </ToggleGroup>
          <p className="text-xs text-muted-foreground">Add at least 3 topics for a balanced feed.</p>
        </CardContent>
        <CardFooter className="justify-end">
          <Button size="sm" onClick={save} disabled={pending}>
            {pending ? "Saving…" : "Save topics"}
          </Button>
        </CardFooter>
      </Card>
    </section>
  );
}
