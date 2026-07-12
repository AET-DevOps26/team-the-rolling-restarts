# Web Client (`web-client/`)

Next.js 16 (canary-ish, React 19), TypeScript, Tailwind v4, shadcn/ui
(`@base-ui/react`). Port 3000, served in front of `reverse-proxy` (nginx) in
`docker-compose.yaml`.

## Structure

```text
instrumentation.ts  OTel registration (@vercel/otel) — see docs/internal/06-observability.md
src/
  app/
    (app)/          dashboard, saved, settings, article/[id]
    (auth)/         login, signup, forgot-password
  components/
    layout/         app-sidebar, app-topbar, topbar-search
    ui/             shadcn primitives
    settings/       profile-section, sources-section, topics-section, notifications-section
    article/        related-articles, ai/ (ArticleAiPanel + widgets)
    feed/           dashboard-feed, article-card, feed-toolbar, feed-search-pagination
    marketing/      sources-strip (landing page)
  lib/
    api/            client.ts (apiFetch, "server-only"), reads.ts, ai.ts, errors.ts, types.ts
    actions/        auth.ts, content.ts, settings.ts — "use server" mutations
    auth/           constants.ts (AUTH_COOKIE)
    format/         time/color helpers
  generated/
    api.ts          TypeScript types generated FROM api/openapi.yaml
```

No `lib/mock/` anymore — that was an early scaffold, fully replaced once the backend integration
landed (`feat/web-client-backend-integration`). If you see a note (memory, stale comment) claiming
the frontend is mock-driven, it predates that work.

Real, non-trivial page inventory exists — this is not a stub UI. The article
detail page consumes live content APIs (`src/lib/api/reads.ts`) and GenAI via
server actions (`src/app/(app)/article/[id]/ai-actions.ts` → `src/lib/api/ai.ts`
→ gateway `/api/ai/*`). See [07-gotchas.md](07-gotchas.md) for remaining gaps
(e.g. client test runner).

## Config files worth knowing about

- `AGENTS.md` (real content) / `CLAUDE.md` (just `@AGENTS.md` — an include)
  — points any AI agent to `$HOME/.agents/skills` for Next.js/shadcn skill
  files (not committed to this repo, machine-local)
- `jest.pact.config.js` — exists but **`jest`/`ts-jest` are not in
  `package.json` devDependencies** and no `tests/pact/` directory exists.
  Dead config, not a working test setup. See [07-gotchas.md](07-gotchas.md).
  This is unrelated to the real unit-test setup below — don't confuse the two.
- **Real unit tests exist via Vitest**: `package.json` has `"test": "vitest run"` and
  `"test:watch": "vitest"`, with real `*.test.ts` files (e.g. `lib/api/client.test.ts`) covering
  the server-only fetch wrapper. Not comprehensive component/page coverage, but a genuinely
  working test runner — not the "no test runner at all" gap this doc used to describe.
- `API_BASE_URL` (not `NEXT_PUBLIC_API_BASE_URL`) drives where `apiFetch` sends requests, read at
  server runtime — deliberately not `NEXT_PUBLIC_`-prefixed, since that would get inlined into the
  bundle at build time and couldn't be overridden per deployment target afterward (see
  [07-gotchas.md](07-gotchas.md)). `COOKIE_SECURE` similarly controls the auth cookie's `Secure`
  flag, set per deployment target rather than inferred from `NODE_ENV`.

## Re-verify

```sh
grep -n '"scripts"' -A8 web-client/package.json
find web-client -iname "*.test.*" -not -path "*/node_modules/*"
grep -rln "generated/api\|apiFetch\b" web-client/src --include="*.tsx" --include="*.ts"
grep -n "API_BASE_URL\|COOKIE_SECURE" web-client/src/lib/api/client.ts web-client/src/lib/actions/auth.ts
```
