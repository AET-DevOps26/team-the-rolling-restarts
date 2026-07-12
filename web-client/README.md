# web-client

The Next.js (App Router) + React 19 + TypeScript frontend for the Personalised News Aggregator.

## Getting started

```bash
npm ci
cp .env.example .env.local   # .env.local is gitignored
npm run dev                  # http://localhost:3000
```

### Scripts

| Script             | Purpose                                                        |
| ------------------ | ------------------------------------------------------------- |
| `npm run dev`      | Start the local dev server                                     |
| `npm run build`    | Production build                                               |
| `npm run start`    | Serve the production build                                     |
| `npm run lint`     | ESLint                                                         |
| `npm run test`     | Run the Vitest unit tests (`npm run test:watch` for watch)    |
| `npm run generate` | Regenerate the typed API client (see below)                   |

## Backend integration

This app is a thin client over the Spring **API gateway**. There is no separate
backend-for-frontend: reads happen server-side and mutations go through Server
Actions, both talking directly to the gateway.

### Configuration

- The gateway base URL is read from `API_BASE_URL` (default `http://localhost:8080`). Copy
  `.env.example` → `.env.local` to set it for local development (`.env.local` is gitignored).
  Deliberately not `NEXT_PUBLIC_`-prefixed: `src/lib/api/client.ts` is `"server-only"`, so this
  value is read at server runtime, never inlined into the browser bundle at build time — the same
  built image works correctly across every deployment target this way.

### Generated API types

- `npm run generate` regenerates `src/generated/api.ts` from
  `../api/openapi.yaml` (the OpenAPI spec is the single source of truth). The
  generated file is **gitignored** — run `npm run generate` after pulling spec
  changes and before typechecking/building.

### Authentication

- Auth uses a JWT stored in an **httpOnly `auth_token` cookie**, set by the
  Server Actions in `src/lib/actions/auth.ts` (login/signup/logout).
- `middleware.ts` guards the authenticated routes `/dashboard`, `/saved`,
  `/settings`, and `/article`, redirecting unauthenticated requests to login.

### Data layer

- **Reads** (server-side): `src/lib/api/reads.ts`, built on the shared
  `apiFetch` helper in `src/lib/api/client.ts`, which attaches the bearer token
  from the cookie and normalises errors to the unified error schema. Reads use
  `cache: no-store` so they are always dynamic.
- **Mutations**: Server Actions under `src/lib/actions/` (e.g. auth, saving
  articles, preferences).

### Frontend-led / not yet backend-persisted

Some UI is implemented on the frontend ahead of backend support and is **not**
persisted server-side yet:

- **Feed sort modes** — *for-you* and *trending* are client-side placeholders.
- **Topbar search** — client-side filtering only.
- **Forgot password** — preview/UI only.
- **Settings: feed preferences, notifications, and appearance** — local-only,
  not saved to the backend.
