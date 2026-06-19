# Spring Microservices

Multi-module Gradle project containing three Spring Boot 4.x microservices.

## Modules

| Module | Port | Description | Database |
|--------|------|-------------|----------|
| `api-gateway` | 8080 | Spring Cloud Gateway MVC — routes, CORS, JWT validation | — |
| `user-service` | 8081 | OAuth2 Authorization Server, user profiles, settings | PostgreSQL |
| `content-service` | 8082 | RSS feed management, articles, topics | MongoDB |
| `generated` | — | Shared OpenAPI-generated models (library only) | — |

## Build

```bash
./gradlew build          # compile + test all modules
./gradlew :api-gateway:bootJar   # build single service jar
```

## Local Development

1. Start databases (from project root):
   ```bash
   docker compose -f infra/docker-compose.yaml up postgres mongodb -d
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
| `SPRING_DATASOURCE_URL` | user-service | PostgreSQL JDBC URL |
| `SPRING_DATASOURCE_USERNAME` | user-service | PostgreSQL username |
| `SPRING_DATASOURCE_PASSWORD` | user-service | PostgreSQL password |
| `SPRING_MONGODB_URI` | content-service | MongoDB connection URI |
| `JWT_ISSUER_URI` | api-gateway, content-service | OAuth2 token issuer (user-service URL) |
| `USER_SERVICE_URL` | api-gateway | Upstream user-service URL |
| `CONTENT_SERVICE_URL` | api-gateway | Upstream content-service URL |
| `CORS_ALLOWED_ORIGINS` | api-gateway | Comma-separated allowed CORS origins |

## OpenAPI Code Generation

The `generated` module is populated by running the OpenAPI generator from the project root:

```bash
npx @openapitools/openapi-generator-cli generate \
  -i api/openapi.yaml -g spring -o services/spring/generated
```

Or use the helper script: `./api/scripts/gen-all.sh`

## Testing

```bash
./gradlew test                       # all modules
./gradlew :user-service:test         # single module
```

Tests use `@WebMvcTest` with `@Import(SecurityConfig.class)` for controller-level tests and `@ExtendWith(MockitoExtension.class)` for service-layer unit tests.
