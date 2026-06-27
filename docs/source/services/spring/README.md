# Spring Microservices

Multi-module Gradle project containing three Spring Boot 4.x microservices.

## Modules

| Module | Port | Description | Database |
|--------|------|-------------|----------|
| `api-gateway` | 8080 | Spring Cloud Gateway MVC — routes, CORS, JWT validation | — |
| `user-service` | 8081 | OAuth2 Authorization Server, user profiles, settings | MongoDB |
| `content-service` | 8082 | RSS feed management, articles, topics | MongoDB |

## Build

```bash
./gradlew build          # compile + test all modules
./gradlew :api-gateway:bootJar   # build single service jar
```

## Local Development

1. Start databases (from project root):
   ```bash
   docker compose -f infra/docker-compose.yaml up mongodb -d
   ```

2. Run each service with the `dev` profile:
   ```bash
   SPRING_PROFILES_ACTIVE=dev ./gradlew :user-service:bootRun
   SPRING_PROFILES_ACTIVE=dev ./gradlew :content-service:bootRun
   SPRING_PROFILES_ACTIVE=dev ./gradlew :api-gateway:bootRun
   ```

   The `dev` profile provides local database credentials and debug logging.

## Spring Profiles

| Profile | Purpose |
|---------|---------|
| `dev` | Local development — hardcoded DB creds, debug logging, all actuator endpoints |
| `production` | Deployed environments — `ddl-auto=validate`, graceful shutdown, restricted actuator |

Base `application.properties` contains no default credentials — they must come from either the active profile or environment variables.

## Environment Variables

| Variable | Service | Description |
|----------|---------|-------------|
| `SPRING_PROFILES_ACTIVE` | all | Active Spring profile (`dev`, `production`) |
| `SPRING_MONGODB_URI` | user-service, content-service | MongoDB connection URI (each service uses a separate database) |
| `JWT_ISSUER` | user-service | Issuer (`iss`) claim stamped on minted JWTs and advertised via OIDC discovery; must equal the resource servers' `JWT_ISSUER_URI` |
| `JWT_ISSUER_URI` | api-gateway, content-service | OAuth2 token issuer used to validate JWTs (user-service URL) |
| `USER_SERVICE_URL` | api-gateway | Upstream user-service URL |
| `CONTENT_SERVICE_URL` | api-gateway | Upstream content-service URL |
| `CORS_ALLOWED_ORIGINS` | api-gateway | Comma-separated allowed CORS origins |

## OpenAPI (Code-First)

The controllers are the source of truth. springdoc generates per-service specs during tests (`OpenApiDocGenerationTest`), which are merged into `api/openapi.yaml` by `api/scripts/gen-all.sh`. Consumer clients (Python, TypeScript) are generated from that spec — no Java server stubs are generated. See [OpenAPI Workflow](../../openapi-workflow.md).

## Testing

```bash
./gradlew test                       # all modules
./gradlew :user-service:test         # single module
```

Tests use `@WebMvcTest` for controller-level tests, `@ExtendWith(MockitoExtension.class)` for service-layer unit tests, and `@SpringBootTest` with Testcontainers for integration smoke tests (health, registration, login).
