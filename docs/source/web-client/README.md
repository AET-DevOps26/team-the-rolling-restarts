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

## Initial routes (structure only)

Placeholder pages and `next/link` navigation—no real auth or API calls yet.

| Path          | Purpose                                      |
| ------------- | -------------------------------------------- |
| `/`           | Marketing landing page (hero, features, CTA) |
| `/login`      | Placeholder sign-in form                     |
| `/signup`     | Placeholder registration form                |
| `/forgot-password` | Password reset request placeholder      |
| `/dashboard`  | Main feed/dashboard placeholder (app shell) |
| `/saved`      | Saved articles placeholder (app shell)      |
| `/settings`   | Preferences placeholder (app shell)         |
| `/article/[id]` | Placeholder article detail view            |

Shared app chrome for dashboard/saved/settings lives in `(app)/layout.tsx` via `AppSidebar` and `AppTopbar`; route metadata is centralized in `src/lib/routes.ts` for easier refactors.

## Local development

```bash
cd web-client
cp .env.example .env.local
npm install
npm run dev          # http://localhost:3000
```

## Scripts

| Command         | Purpose                                  |
| --------------- | ---------------------------------------- |
| `npm run dev`   | Start dev server (Turbopack, hot reload) |
| `npm run build` | Production build                         |
| `npm run start` | Run the production build                 |
| `npm run lint`  | ESLint                                   |

## Adding shadcn components

```bash
npx shadcn@latest add <component>      # e.g. dialog, input, dropdown-menu
```

Components land in `src/components/ui/` and can be edited freely — they are
copied into the repo, not imported as a runtime dependency.

## Backend integration

The client consumes the OpenAPI spec at `api/openapi.yaml` to generate a typed TypeScript client (in `src/generated/`). Backend services (`services/spring/api-gateway`, `services/spring/user-service`, `services/spring/content-service`, `services/gen-ai`) are reached through the API gateway.

`NEXT_PUBLIC_API_BASE_URL` in `.env.local` controls where the client sends requests; the value is inlined into the browser bundle at build time.

## Notes for agents

`AGENTS.md` warns that this Next.js version may differ from older training data. Check `node_modules/next/dist/docs/` if APIs behave unexpectedly. For UI and React patterns, see **`$HOME/.agents/skills`** as described in `AGENTS.md`.
