import { getSources } from "@/lib/api/reads";

export async function SourcesStrip() {
  let displayed: { id: string; name: string }[] = [];
  try {
    displayed = (await getSources()).slice(0, 8);
  } catch {
    displayed = [];
  }
  if (displayed.length === 0) return null;

  return (
    <section className="border-y border-border bg-muted/30 py-8">
      <div className="mx-auto flex w-full max-w-6xl flex-col items-center gap-4 px-4">
        <p className="text-xs uppercase tracking-wider text-muted-foreground">
          Powered by trusted sources
        </p>
        <ul className="flex flex-wrap items-center justify-center gap-x-6 gap-y-2 text-sm text-muted-foreground">
          {displayed.map((source, i) => (
            <li key={source.id} className="flex items-center gap-6">
              <span>{source.name}</span>
              {i < displayed.length - 1 && (
                <span aria-hidden className="text-muted-foreground/50">
                  ·
                </span>
              )}
            </li>
          ))}
        </ul>
      </div>
    </section>
  );
}
