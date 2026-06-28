"use client";

import { useState } from "react";

import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Field, FieldGroup, FieldLabel } from "@/components/ui/field";
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group";
import { Switch } from "@/components/ui/switch";

export function FeedPrefsSection() {
  const [sort, setSort] = useState("newest");
  const [density, setDensity] = useState("comfortable");
  const [showImages, setShowImages] = useState(true);

  return (
    <section id="feed-preferences" className="scroll-mt-20">
      <Card>
        <CardHeader>
          <CardTitle>Feed preferences</CardTitle>
          <CardDescription>
            How your feed is ordered and displayed.
          </CardDescription>
        </CardHeader>
        <CardContent className="flex flex-col gap-6">
          <p className="rounded-md border border-border bg-muted/40 p-2 text-xs text-muted-foreground">
            Saved locally only — server sync coming soon.
          </p>
          <FieldGroup>
            <Field>
              <FieldLabel>Sort order</FieldLabel>
              <RadioGroup
                value={sort}
                onValueChange={(value) => {
                  if (typeof value === "string") setSort(value);
                }}
              >
                <label className="flex items-center gap-2 text-sm">
                  <RadioGroupItem value="newest" /> Newest first
                </label>
                <label className="flex items-center gap-2 text-sm">
                  <RadioGroupItem value="relevant" /> Most relevant
                </label>
              </RadioGroup>
            </Field>
            <Field>
              <FieldLabel>Density</FieldLabel>
              <RadioGroup
                value={density}
                onValueChange={(value) => {
                  if (typeof value === "string") setDensity(value);
                }}
              >
                <label className="flex items-center gap-2 text-sm">
                  <RadioGroupItem value="comfortable" /> Comfortable
                </label>
                <label className="flex items-center gap-2 text-sm">
                  <RadioGroupItem value="compact" /> Compact
                </label>
              </RadioGroup>
            </Field>
            <Field className="flex flex-row items-center justify-between">
              <FieldLabel>Show images</FieldLabel>
              <Switch
                checked={showImages}
                onCheckedChange={setShowImages}
                aria-label="Show images"
              />
            </Field>
          </FieldGroup>
        </CardContent>
      </Card>
    </section>
  );
}
