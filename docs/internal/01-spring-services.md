# Spring Services (`services/spring/`)

Gradle multi-module project (`services/spring/settings.gradle`), 3 modules,
package root `rolling_restarts.*` (note: underscore, not the usual `rollingrestarts`).

## api-gateway (port 8080)

- Package: `rolling_restarts.gateway` — `config/`, `controller/`, `exception/`
- Only controller: `RootController.java` (likely just a root/health route —
  routing itself is Spring Cloud Gateway config, not hand-written controllers)
- Role: routes traffic to user-service/content-service/gen-ai, validates JWTs as a
  resource server, aggregates Swagger UI for all services
  (`springdoc.swagger-ui.urls[...]` in `application.properties`). Public (no JWT):
  `/api/users/auth/**`, GET content reads, `/api/ai/**`.
- **Does not count as one of the required ≥3 business-domain microservices**
  — it's routing/infra. See `docs/requirements/STATUS.md` §03.
- Tests: 5 files, e.g. `SubscriberScopeTest.java`

## user-service (port 8081)

- Package: `rolling_restarts.user` — `client/`, `config/`, `controller/`, `exception/`, `model/`, `repository/`, `service/`
- Controllers: `AuthController.java`, `UserController.java`
- Role: OAuth2 Authorization Server, user profiles/settings, auth
- `client/` package — outbound calls to another service (likely content-service, e.g. topic/source lookups)
- Tests: 6 files, e.g. `AuthControllerTest.java`, `UserServiceTest.java`

## content-service (port 8082)

- Package: `rolling_restarts.content` — `config/`, `controller/`, `exception/`, `model/`, `repository/`, `scheduler/`, `service/`, `util/`
- Controllers: `ArticleController.java`, `SourceController.java`, `TopicController.java`
- Role: RSS feed ingestion (`scheduler/` — scheduled fetch job), article storage, topics/sources
- Tests: 12 files, e.g. `ArticleControllerTest.java`, `SourceServiceTest.java`, `RssFetcherServiceTest.java`, `PinnedDnsResolverProviderTest.java`

## Cross-cutting

- DB: MongoDB (`image: mongo:8`), schemaless — no Flyway/Liquibase, no
  dedicated schema doc beyond the `model/` entity classes
- Profiles: `dev` (default creds, safe to hardcode) vs `production` (env vars
  only, no secrets in `application-production.properties`)
- API contract: `api/openapi.yaml`, generated code-first from user-service +
  content-service controllers (via springdoc + `OpenApiDocGenerationTest`,
  merged with gateway route prefixes `/api/users`, `/api/content`) — see
  `docs/internal/05-ci-cd-workflows.md` and `api/scripts/gen-all.sh`.
  **api-gateway does not export its own spec** — it only aggregates the
  other two in Swagger UI.
- Ignore `services/spring-api/` if you ever see it — it's a local,
  gitignored (`**/generated`) leftover from `openapi-generator`'s default
  Java-server-stub template (`DummyApi`, `TestApi` — generic boilerplate
  names, not project code). It is **not** part of the real pipeline, which
  explicitly generates no server stubs (see `api/scripts/gen-all.sh`'s header
  comment).

## Re-verify

```sh
cat services/spring/settings.gradle                    # module list
find services/spring/*/src/main/java -iname "*Controller.java"
git ls-files services/spring-api | wc -l                # should stay 0 (untracked)
```
