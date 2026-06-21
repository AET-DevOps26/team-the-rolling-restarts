# CLAUDE.md — Spring Services

## Current Work Status

### Gateway routing fix (IN PROGRESS)

The gateway routes were returning 500 for all proxied endpoints (`/api/users/**`, `/api/content/**`). Root cause identified and partially fixed:

**The Spring Cloud Gateway MVC property prefix changed in Spring Cloud 2025.1.1:**
- Old (broken): `spring.cloud.gateway.mvc.routes[*]`
- New (correct): `spring.cloud.gateway.server.webmvc.routes[*]`

The `application.properties` has been updated with the new prefix. **Still needs testing:**

1. Clean the stale build output (root-owned files from Docker):
   ```bash
   docker run --rm -v $(pwd):/workspace gradle:jdk-25-and-25 sh -c "rm -rf /workspace/api-gateway/build"
   ```
2. Restart:
   ```bash
   docker compose --env-file infra/.env -f infra/docker-compose.yaml -f infra/docker-compose.dev.yaml restart api-gateway
   ```
3. Verify routing works:
   ```bash
   curl http://localhost:8080/api/content/sources   # should return [] not 500
   curl -X POST http://localhost:8080/api/users/auth/register \
     -H "Content-Type: application/json" \
     -d '{"username":"test","email":"t@t.com","password":"password123","name":"Test"}'
   ```

### Route prefix stripping

The downstream services don't use `/api/users` or `/api/content` prefixes in their controllers:
- user-service controllers: `/auth/**`, `/users/**`
- content-service controllers: `/sources`, `/topics`, `/articles`

The gateway uses `StripPrefix` filters:
- `StripPrefix=1` on user-service: strips `/api` → forwards `/users/**`
- `StripPrefix=2` on content-service: strips `/api/content` → forwards `/**`

### SecurityConfig public endpoints

The gateway SecurityConfig permits these public paths (no JWT required):
- `/api/users/auth/**` — registration
- GET `/api/content/sources`, `/api/content/topics`, `/api/content/articles` — public reads
- Swagger UI, actuator health, root endpoints

All other routes require JWT Bearer token.

---

## What was done in this session

### Phase 1: API Gateway
- CORS configuration via `CorsConfigurationSource` bean (env var `CORS_ALLOWED_ORIGINS`)
- `GlobalExceptionHandler` with unified error schema `{timestamp, code, message, details, path}`
- Spring profiles: `application-dev.properties`, `application-production.properties`

### Phase 2: User Service (MongoDB)
- Migrated from PostgreSQL/JPA to MongoDB — shares the MongoDB instance with content-service using a separate `users` database
- UserSettings embedded in User document (single-document atomic writes, no `@Transactional` needed)
- Input validation: `@NotBlank`, `@Email`, `@Size` on `RegisterRequest`, `@Valid` on all request bodies
- `GlobalExceptionHandler`: validation → 400, duplicate → 409, not-found → 404
- Spring profiles: dev (local MongoDB URI) + production (graceful shutdown)
- RSA key persistence: loads from `jwt.rsa.public-key`/`jwt.rsa.private-key` when set, falls back to generated
- Tests: `AuthControllerTest` (5 tests), `UserServiceTest` (7 tests)

### Phase 3: Content Service (MongoDB)
- Input validation: `@NotBlank` on `CreateSourceRequest`, pagination limits (max 100, default 20)
- `GlobalExceptionHandler` with same error schema
- Spring profiles: dev (local MongoDB URI) + production (graceful shutdown)
- Tests: `SourceControllerTest` (7 tests), `ArticleControllerTest` (4 tests)

### Build system
- Added `spring-boot-starter-validation` to root `build.gradle` subprojects
- Added `spring-boot-starter-webmvc-test` to user-service and content-service
- Fixed `.openapi-generator-ignore` to exclude `src/test/**` (generated test had no test deps)
- Note: Spring Boot 4.x moved `@WebMvcTest` to `org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest`

### Docker Compose dev
- Fixed Gradle lock contention: each service gets its own `GRADLE_USER_HOME` subdirectory + `--project-cache-dir /tmp/gradle-project`

## Build & Test

```bash
# Generate OpenAPI stubs first (required before building)
npm ci
python -m pip install openapi-python-client
./api/scripts/gen-all.sh

# Build and test
cd services/spring
./gradlew build    # should pass all tests
```

## Key Gotchas

- Spring Boot 4.x: `@WebMvcTest` is at `org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest` (not the old `org.springframework.boot.test.autoconfigure.web.servlet`)
- Spring Cloud 2025.x: gateway MVC property prefix is `spring.cloud.gateway.server.webmvc` (not `spring.cloud.gateway.mvc`)
- OpenAPI generator creates a test file in `generated/src/test/` that doesn't compile — `.openapi-generator-ignore` excludes it with `src/test/**`
- Base `application.properties` has NO default credentials — use `dev` profile or set env vars
