# Web Client Integration Notes

The backend has been restructured from a single `spring-api` monolith into three
microservices behind an API gateway. The web-client team should update the
frontend to integrate with the new API surface.

## What changed

The single `spring-api` has been replaced by:

| Service           | Internal Port | Role                                              |
| ----------------- | ------------- | ------------------------------------------------- |
| `api-gateway`     | 8080          | Routes traffic, JWT validation, unified Swagger UI |
| `user-service`    | 8081          | OAuth2 Authorization Server, user profiles/settings |
| `content-service` | 8082          | RSS feeds, articles, topics                        |

The web client talks to the nginx reverse proxy (`NEXT_PUBLIC_API_BASE_URL`) which
forwards to the gateway. Locally the proxy is on port 8080 (`APP_PORT`), on the VM
it is on port 80. The gateway forwards requests based on path prefix.

## New API endpoints

### User service (via `/api/users/`)

- `POST /api/users/auth/register` — register a new user
- `POST /api/users/oauth2/token` — OAuth2 token endpoint (login)
- `GET  /api/users/users/me` — current user profile
- `PUT  /api/users/users/me` — update profile
- `GET  /api/users/users/me/settings` — user preferences
- `PUT  /api/users/users/me/settings` — update preferences
- `POST   /api/users/users/me/subscriptions/{sourceId}` — subscribe to a source
- `DELETE /api/users/users/me/subscriptions/{sourceId}` — unsubscribe from a source

### Content service (via `/api/content/`)

- `GET    /api/content/sources` — list RSS sources
- `POST   /api/content/sources` — submit a new RSS feed URL
- `GET    /api/content/sources/{id}` — source details (includes `subscriberCount`)
- `GET    /api/content/topics` — list topics
- `GET    /api/content/articles` — paginated articles (query params: `sourceId`, `topicId`)
- `GET    /api/content/articles/{id}` — full article
- `POST   /api/content/articles/saved` — batch-get articles by IDs

## What should be updated in web-client

- **`web-client/README.md`** — the "Backend integration" section still references
  placeholder service names (`spring-order`, `py-recommender`). Update it to
  document the real gateway URL and the API paths listed above.
- **Mock data** — `src/lib/mock/` can be replaced with real API calls once the
  services are running.
- **Auth flow** — wire up login/signup forms to the OAuth2 token endpoint and
  store the JWT for subsequent requests.
- **Generated types** — the API is code-first: `api/openapi.yaml` is generated
  from the Spring controllers, and `web-client/src/generated/api.ts` is generated
  from that contract (`make generate`, or `npx openapi-typescript api/openapi.yaml
  -o web-client/src/generated/api.ts`). See [OpenAPI workflow](openapi-workflow.md).
  The file is gitignored, so CI and the image build regenerate it before building.
