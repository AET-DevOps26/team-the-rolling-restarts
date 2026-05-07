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
│   │   ├── page.tsx        landing placeholder
│   │   └── globals.css     Tailwind + shadcn theme tokens
│   ├── components/ui/      shadcn primitives (button, card)
│   └── lib/utils.ts        shadcn `cn()` helper
├── components.json         shadcn config
├── next.config.ts
├── tsconfig.json           import alias `@/*` -> `./src/*`
└── eslint.config.mjs
```

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

This app is currently a static scaffold. Once the backend services (`services/spring-order`, `services/py-recommender`) are reachable through an API gateway, the client should consume the OpenAPI spec at `api/openapi.yaml` to generate a typed client.

`NEXT_PUBLIC_API_BASE_URL` in `.env.local` controls where the client sends requests; the value is inlined into the browser bundle at build time.

## Notes for agents

`AGENTS.md` (committed by `create-next-app`) warns that this Next.js version may differ from older training data. Check `node_modules/next/dist/docs/` if APIs behave unexpectedly.
