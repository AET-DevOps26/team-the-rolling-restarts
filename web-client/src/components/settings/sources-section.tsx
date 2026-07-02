"use client";

import { AlertCircle, CheckCircle2, Loader2, X } from "lucide-react";
import { useRouter } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { toast } from "sonner";

import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Field, FieldGroup, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { Switch } from "@/components/ui/switch";
import { addSource, getSourceFetchStatus, subscribe, unsubscribe } from "@/lib/actions/content";
import type { Source } from "@/lib/api/types";

const POLL_INTERVAL_MS = 2000;
const MAX_POLLS = 20;

type FetchState =
  | { phase: "fetching"; name: string }
  | { phase: "success"; name: string }
  | { phase: "failed"; name: string; error: string }
  | { phase: "timeout"; name: string }
  | null;

export function SourcesSection({
  sources,
  enabledSourceIds,
}: {
  sources: Source[];
  enabledSourceIds: string[];
}) {
  const router = useRouter();
  const [enabled, setEnabled] = useState<Set<string>>(() => new Set(enabledSourceIds));
  const [pending, setPending] = useState(false);
  const [adding, setAdding] = useState(false);
  const [name, setName] = useState("");
  const [rssUrl, setRssUrl] = useState("");
  const [fetchState, setFetchState] = useState<FetchState>(null);

  // Re-seed the toggles whenever the server sends refreshed subscriptions (e.g. after adding a
  // source), otherwise a newly added source stays visually "off" until a full page reload.
  const enabledKey = enabledSourceIds.join(",");
  useEffect(() => {
    setEnabled(new Set(enabledSourceIds));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [enabledKey]);

  // Bumped whenever a new poll starts (or the component unmounts) so stale polls exit early.
  const pollTokenRef = useRef(0);
  useEffect(() => () => void (pollTokenRef.current += 1), []);

  async function toggle(sourceId: string, next: boolean) {
    setEnabled((current) => {
      const updated = new Set(current);
      if (next) updated.add(sourceId);
      else updated.delete(sourceId);
      return updated;
    });
    setPending(true);
    try {
      const res = next ? await subscribe(sourceId) : await unsubscribe(sourceId);
      if (!res.ok) {
        setEnabled((current) => {
          const reverted = new Set(current);
          if (next) reverted.delete(sourceId);
          else reverted.add(sourceId);
          return reverted;
        });
        toast.error(res.error);
      }
    } finally {
      setPending(false);
    }
  }

  async function pollStatus(sourceId: string, sourceName: string) {
    const token = (pollTokenRef.current += 1);
    for (let attempt = 0; attempt < MAX_POLLS; attempt++) {
      await new Promise((resolve) => setTimeout(resolve, POLL_INTERVAL_MS));
      if (pollTokenRef.current !== token) return; // superseded or unmounted
      const res = await getSourceFetchStatus(sourceId);
      if (pollTokenRef.current !== token) return;
      if (!res.ok) continue; // transient error — keep polling
      if (res.status === "SUCCESS") {
        setFetchState({ phase: "success", name: sourceName });
        toast.success(`Articles from ${sourceName} are ready`);
        router.refresh();
        return;
      }
      if (res.status === "FAILED") {
        setFetchState({
          phase: "failed",
          name: sourceName,
          error: res.error ?? "The feed could not be read.",
        });
        toast.error(`Couldn't fetch ${sourceName}`);
        router.refresh();
        return;
      }
      // PENDING → keep polling
    }
    setFetchState({ phase: "timeout", name: sourceName });
  }

  async function handleAddSource(e: React.FormEvent) {
    e.preventDefault();
    setAdding(true);
    try {
      const addedName = name.trim();
      const res = await addSource(name, rssUrl);
      if (res.ok) {
        toast.success("Source added — fetching latest articles…");
        setName("");
        setRssUrl("");
        setFetchState({ phase: "fetching", name: addedName });
        router.refresh(); // surface the new source in the list right away
        void pollStatus(res.sourceId, addedName);
      } else {
        toast.error(res.error);
      }
    } finally {
      setAdding(false);
    }
  }

  return (
    <section id="sources" className="scroll-mt-20">
      <Card>
        <CardHeader>
          <CardTitle>Sources</CardTitle>
          <CardDescription>
            Add RSS feeds you follow, then toggle which ones appear in your feed.
          </CardDescription>
        </CardHeader>
        <CardContent className="flex flex-col gap-6">
          <form onSubmit={handleAddSource} className="rounded-lg border border-border p-4">
            <FieldGroup>
              <Field>
                <FieldLabel htmlFor="source-name">Publication name</FieldLabel>
                <Input
                  id="source-name"
                  name="name"
                  placeholder="e.g. Süddeutsche Zeitung"
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  disabled={adding}
                />
              </Field>
              <Field>
                <FieldLabel htmlFor="source-rss-url">RSS feed URL</FieldLabel>
                <Input
                  id="source-rss-url"
                  name="rssUrl"
                  type="url"
                  placeholder="https://rss.sueddeutsche.de/alles"
                  value={rssUrl}
                  onChange={(e) => setRssUrl(e.target.value)}
                  disabled={adding}
                />
              </Field>
            </FieldGroup>
            <Button type="submit" size="sm" className="mt-4" disabled={adding}>
              {adding ? "Adding…" : "Add source"}
            </Button>

            {fetchState && <FetchStatusBanner state={fetchState} onDismiss={() => setFetchState(null)} />}
          </form>

          {sources.length === 0 ? (
            <p className="text-sm text-muted-foreground">
              No sources yet — add an RSS feed above to start building your feed.
            </p>
          ) : (
            <ul className="flex flex-col divide-y divide-border">
              {sources.map((source) => (
                <li key={source.id} className="flex items-center gap-4 py-3 first:pt-0 last:pb-0">
                  <Avatar className="size-9">
                    <AvatarFallback>{source.initials}</AvatarFallback>
                  </Avatar>
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center gap-2">
                      <p className="truncate text-sm font-medium">{source.name}</p>
                      <SourceStatusBadge source={source} />
                    </div>
                    <p className="text-xs text-muted-foreground">
                      {source.subscriberCount} subscriber{source.subscriberCount === 1 ? "" : "s"}
                    </p>
                    {source.fetchStatus === "FAILED" && source.fetchError && (
                      <p className="mt-0.5 text-xs text-destructive">{source.fetchError}</p>
                    )}
                  </div>
                  <Switch
                    checked={enabled.has(source.id)}
                    disabled={pending}
                    onCheckedChange={(next) => toggle(source.id, next)}
                    aria-label={`Toggle ${source.name}`}
                  />
                </li>
              ))}
            </ul>
          )}
        </CardContent>
      </Card>
    </section>
  );
}

function SourceStatusBadge({ source }: { source: Source }) {
  if (source.fetchStatus === "PENDING") {
    return (
      <Badge variant="secondary" className="gap-1">
        <Loader2 className="size-3 animate-spin" aria-hidden />
        Fetching
      </Badge>
    );
  }
  if (source.fetchStatus === "FAILED") {
    return (
      <Badge variant="destructive" className="gap-1">
        <AlertCircle className="size-3" aria-hidden />
        Fetch failed
      </Badge>
    );
  }
  return null;
}

function FetchStatusBanner({
  state,
  onDismiss,
}: {
  state: NonNullable<FetchState>;
  onDismiss: () => void;
}) {
  if (state.phase === "fetching" || state.phase === "timeout") {
    return (
      <div
        role="status"
        aria-live="polite"
        className="mt-4 flex items-center gap-2 rounded-md bg-muted px-3 py-2 text-sm text-muted-foreground"
      >
        <Loader2 className="size-4 animate-spin" aria-hidden />
        {state.phase === "fetching"
          ? `Fetching latest articles from ${state.name}…`
          : `Still fetching ${state.name}. Its articles will appear in your feed shortly.`}
      </div>
    );
  }

  if (state.phase === "success") {
    return (
      <div
        role="status"
        aria-live="polite"
        className="mt-4 flex items-center justify-between gap-2 rounded-md bg-emerald-500/10 px-3 py-2 text-sm text-emerald-600 dark:text-emerald-400"
      >
        <span className="flex items-center gap-2">
          <CheckCircle2 className="size-4" aria-hidden />
          {state.name} is ready — new articles added to your feed.
        </span>
        <DismissButton onDismiss={onDismiss} />
      </div>
    );
  }

  return (
    <div
      role="alert"
      className="mt-4 flex items-start justify-between gap-2 rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive"
    >
      <span className="flex items-start gap-2">
        <AlertCircle className="mt-0.5 size-4 shrink-0" aria-hidden />
        <span>
          Couldn&apos;t fetch {state.name}. {state.error}
        </span>
      </span>
      <DismissButton onDismiss={onDismiss} />
    </div>
  );
}

function DismissButton({ onDismiss }: { onDismiss: () => void }) {
  return (
    <button
      type="button"
      onClick={onDismiss}
      className="shrink-0 rounded p-0.5 opacity-70 transition hover:opacity-100"
      aria-label="Dismiss"
    >
      <X className="size-4" aria-hidden />
    </button>
  );
}
