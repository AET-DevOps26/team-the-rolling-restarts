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
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Switch } from "@/components/ui/switch";

export function NotificationsSection() {
  const [emailDigest, setEmailDigest] = useState(true);
  const [emailFrequency, setEmailFrequency] = useState("daily");
  const [breaking, setBreaking] = useState(false);
  const [weekly, setWeekly] = useState(true);

  return (
    <section id="notifications" className="scroll-mt-20">
      <Card>
        <CardHeader>
          <CardTitle>Notifications</CardTitle>
          <CardDescription>When and how NewsLens reaches out.</CardDescription>
        </CardHeader>
        <CardContent className="flex flex-col gap-4">
          <p className="rounded-md border border-border bg-muted/40 p-2 text-xs text-muted-foreground">
            Saved locally only — server sync coming soon.
          </p>
          <div className="flex items-center justify-between gap-4 rounded-lg border border-border p-4">
            <div className="flex flex-col gap-1">
              <p className="text-sm font-medium">Email digest</p>
              <p className="text-xs text-muted-foreground">
                A short summary of your feed.
              </p>
            </div>
            <div className="flex items-center gap-3">
              <Select
                value={emailFrequency}
                onValueChange={(value) => {
                  if (value) setEmailFrequency(value);
                }}
                disabled={!emailDigest}
              >
                <SelectTrigger className="w-32" aria-label="Email frequency">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="daily">Daily</SelectItem>
                  <SelectItem value="weekly">Weekly</SelectItem>
                  <SelectItem value="off">Off</SelectItem>
                </SelectContent>
              </Select>
              <Switch
                checked={emailDigest}
                onCheckedChange={setEmailDigest}
                aria-label="Email digest"
              />
            </div>
          </div>
          <div className="flex items-center justify-between gap-4 rounded-lg border border-border p-4">
            <div className="flex flex-col gap-1">
              <p className="text-sm font-medium">Breaking news alerts</p>
              <p className="text-xs text-muted-foreground">
                We&apos;ll only ping you for major events.
              </p>
            </div>
            <Switch
              checked={breaking}
              onCheckedChange={setBreaking}
              aria-label="Breaking news"
            />
          </div>
          <div className="flex items-center justify-between gap-4 rounded-lg border border-border p-4">
            <div className="flex flex-col gap-1">
              <p className="text-sm font-medium">Weekly summary</p>
              <p className="text-xs text-muted-foreground">
                Your top reads from the past seven days.
              </p>
            </div>
            <Switch
              checked={weekly}
              onCheckedChange={setWeekly}
              aria-label="Weekly summary"
            />
          </div>
        </CardContent>
      </Card>
    </section>
  );
}
