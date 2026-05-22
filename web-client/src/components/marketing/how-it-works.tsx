const STEPS = [
  {
    title: "Pick your topics",
    description: "Choose from ten broad areas — technology, world, sports, more.",
  },
  {
    title: "Choose your sources",
    description: "Subscribe to the publications you trust; mute the rest.",
  },
  {
    title: "Get your feed",
    description: "Open NewsLens to a feed that already knows what you wanted to read.",
  },
];

export function HowItWorks() {
  return (
    <section className="mx-auto w-full max-w-6xl px-4 py-20">
      <div className="flex flex-col items-center gap-3 text-center">
        <h2 className="text-3xl font-semibold tracking-tight">How it works</h2>
        <p className="max-w-2xl text-muted-foreground">
          Three steps and you&apos;re done. No algorithm to game, no infinite scroll.
        </p>
      </div>
      <ol className="mt-10 grid gap-8 md:grid-cols-3">
        {STEPS.map((step, i) => (
          <li
            key={step.title}
            className="flex flex-col items-start gap-3 rounded-lg border border-border bg-card p-6"
          >
            <span className="flex size-8 items-center justify-center rounded-full bg-primary text-sm font-semibold text-primary-foreground">
              {i + 1}
            </span>
            <h3 className="text-lg font-medium">{step.title}</h3>
            <p className="text-sm text-muted-foreground">{step.description}</p>
          </li>
        ))}
      </ol>
    </section>
  );
}
