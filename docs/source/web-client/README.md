# web-client

Next.js client for the Personalised News Aggregator.

## Stack

- Next.js (App Router) with Turbopack
- React 19, TypeScript (strict)
- Tailwind CSS v4
- shadcn/ui (`base-nova` preset, neutral base color, lucide icons)
- ESLint (flat config)
- npm

## Project layout

```
web-client/
├── public/                 static assets
├── src/
│   ├── app/                routes (App Router)
│   │   ├── layout.tsx
│   │   ├── page.tsx        marketing landing page (hero/features/CTA)
│   │   ├── (app)/          route group: shared app shell (nav)
│   │   │   ├── layout.tsx
│   │   │   ├── dashboard/
│   │   │   └── settings/
│   │   ├── (auth)/         route group: unauthenticated flows
│   │   │   └── login/
│   │   └── globals.css     Tailwind + shadcn theme tokens
│   ├── components/
│   │   ├── layout/         app chrome (e.g. AppSidebar/AppTopbar)
│   │   └── ui/             shadcn primitives (button, card)
│   └── lib/
│       ├── routes.ts       path constants + nav/hub metadata
│       └── utils.ts        shadcn `cn()` helper
├── components.json         shadcn config
├── next.config.ts
├── tsconfig.json           import alias `@/*` -> `./src/*`
└── eslint.config.mjs
```

## Routes

Backend-integrated — real auth and API calls, not placeholders (see
[Backend integration](#backend-integration) below and
[docs/internal/02-web-client.md](../../internal/02-web-client.md)).

| Path               | Purpose                                                                                 |
|--------------------|-----------------------------------------------------------------------------------------|
| `/`                | Marketing landing page (hero, features, CTA)                                            |
| `/login`           | Sign-in form, calls `/api/users/auth/login`                                             |
| `/signup`          | Registration form, calls `/api/users/auth/register`                                     |
| `/forgot-password` | Password reset request                                                                  |
| `/dashboard`       | Main feed, live content from `/api/content/*`                                           |
| `/saved`           | Saved articles, backed by the atomic saved-article endpoints                            |
| `/settings`        | Preferences + source subscribe/unsubscribe                                              |
| `/article/[id]`    | Article detail view with live content + the GenAI panel (summary/explain/sentiment/Q&A) |

Shared app chrome for dashboard/saved/settings lives in `(app)/layout.tsx` via `AppSidebar` and `AppTopbar`; route metadata is centralized in `src/lib/routes.ts` for easier refactors.

## Local development

```bash
cd web-client
cp .env.example .env.local
npm install
npm run dev          # http://localhost:3000
```

## Scripts

| Command         | Purpose                                                    |
| --------------- | ---------------------------------------------------------- |
| `npm run dev`   | Start dev server (Turbopack, hot reload)                   |
| `npm run build` | Production build                                           |
| `npm run start` | Run the production build                                   |
| `npm run lint`  | ESLint                                                     |
| `npm run test`  | Vitest — utility/data-layer tests (`src/lib/**/*.test.ts`) |

## Adding shadcn components

```bash
npx shadcn@latest add <component>      # e.g. dialog, input, dropdown-menu
```

Components land in `src/components/ui/` and can be edited freely — they are
copied into the repo, not imported as a runtime dependency.

## Backend integration

The client consumes the OpenAPI spec at `api/openapi.yaml` to generate a typed TypeScript client (in `src/generated/`). Backend services (`services/spring/api-gateway`, `services/spring/user-service`, `services/spring/content-service`, `services/gen-ai`) are reached through the API gateway.

`API_BASE_URL` in `.env.local` controls where the client sends requests. It's deliberately not `NEXT_PUBLIC_`-prefixed: every API call goes through `src/lib/api/client.ts`, which is `"server-only"` and never runs in the browser, so the value is read at server runtime rather than inlined into the bundle at build time — letting the same built image be deployed anywhere, with each target setting its own correct value.

## Notes for agents

`AGENTS.md` warns that this Next.js version may differ from older training data. Check `node_modules/next/dist/docs/` if APIs behave unexpectedly. For UI and React patterns, see **`$HOME/.agents/skills`** as described in `AGENTS.md`.
