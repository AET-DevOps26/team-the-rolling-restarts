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
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";

export function AppearanceSection() {
  const [theme, setTheme] = useState("system");
  const [fontSize, setFontSize] = useState("medium");
  const [accent, setAccent] = useState("default");

  return (
    <section id="appearance" className="scroll-mt-20">
      <Card>
        <CardHeader>
          <CardTitle>Appearance</CardTitle>
          <CardDescription>
            Theme switching ships in a later release.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <p className="rounded-md border border-border bg-muted/40 p-2 text-xs text-muted-foreground">
            Preview only — changes reset when you leave this page. Server sync coming soon.
          </p>
          <FieldGroup>
            <Field>
              <FieldLabel>Theme</FieldLabel>
              <RadioGroup
                value={theme}
                onValueChange={(value) => {
                  if (typeof value === "string") setTheme(value);
                }}
              >
                <label className="flex items-center gap-2 text-sm">
                  <RadioGroupItem value="light" /> Light
                </label>
                <label className="flex items-center gap-2 text-sm">
                  <RadioGroupItem value="dark" /> Dark
                </label>
                <label className="flex items-center gap-2 text-sm">
                  <RadioGroupItem value="system" /> System
                </label>
              </RadioGroup>
            </Field>
            <Field>
              <FieldLabel>Font size</FieldLabel>
              <Select
                value={fontSize}
                onValueChange={(value) => {
                  if (value) setFontSize(value);
                }}
              >
                <SelectTrigger className="w-40">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="small">Small</SelectItem>
                  <SelectItem value="medium">Medium</SelectItem>
                  <SelectItem value="large">Large</SelectItem>
                </SelectContent>
              </Select>
            </Field>
            <Field>
              <FieldLabel>Accent colour</FieldLabel>
              <Select
                value={accent}
                onValueChange={(value) => {
                  if (value) setAccent(value);
                }}
              >
                <SelectTrigger className="w-40">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="default">Default</SelectItem>
                  <SelectItem value="indigo">Indigo</SelectItem>
                  <SelectItem value="emerald">Emerald</SelectItem>
                  <SelectItem value="rose">Rose</SelectItem>
                </SelectContent>
              </Select>
            </Field>
          </FieldGroup>
        </CardContent>
      </Card>
    </section>
  );
}
