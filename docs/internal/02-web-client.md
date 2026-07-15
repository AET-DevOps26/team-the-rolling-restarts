# Web Client (`web-client/`)

Next.js 16 (canary-ish, React 19), TypeScript, Tailwind v4, shadcn/ui
(`@base-ui/react`). Port 3000, served in front of `reverse-proxy` (nginx) in
`docker-compose.yaml`.

## Structure

```text
src/
  app/
    (app)/          dashboard, saved, settings, article/[id]
    (auth)/         login, signup, forgot-password
  components/
    layout/         app-sidebar, app-topbar
    ui/             shadcn primitives
    settings/       profile-section, sources-section, topics-section, notifications-section
    article/        related-articles, ai/ (ArticleAiPanel + widgets)
    feed/           dashboard-feed, article-card, feed-toolbar
    marketing/      sources-strip (landing page)
  lib/
    mock/           articles.ts, sources.ts, topics.ts, user.ts, time.ts, types.ts
  generated/
    api.ts          TypeScript types generated FROM api/openapi.yaml
```

Real, non-trivial page inventory exists — this is not a stub UI. The article
detail page consumes live content APIs (`src/lib/api/reads.ts`) and GenAI via
server actions (`src/app/(app)/article/[id]/ai-actions.ts` → `src/lib/api/ai.ts`
→ gateway `/api/ai/*`). Save/unsave uses atomic user-service endpoints via
`src/lib/actions/content.ts` (`POST`/`DELETE /api/users/users/me/saved-articles/{id}`),
not a whole-settings GET-then-PUT. See [07-gotchas.md](07-gotchas.md) for remaining gaps
(e.g. client test runner).

## Config files worth knowing about

- `AGENTS.md` (real content) / `CLAUDE.md` (just `@AGENTS.md` — an include)
  — points any AI agent to `$HOME/.agents/skills` for Next.js/shadcn skill
  files (not committed to this repo, machine-local)
- `jest.pact.config.js` — exists but **`jest`/`ts-jest` are not in
  `package.json` devDependencies** and no `tests/pact/` directory exists.
  Dead config, not a working test setup. See [07-gotchas.md](07-gotchas.md).
- `package.json` `scripts`: only `dev`, `build`, `start`, `lint` — **no
  `test` script, no test runner dependency at all**.

## Re-verify

```sh
grep -n '"scripts"' -A6 web-client/package.json
find web-client -iname "*.test.*" -not -path "*/node_modules/*"
grep -rln "generated/api\|fetch(\|axios\|NEXT_PUBLIC_API" web-client/src --include="*.tsx" --include="*.ts"
```
