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
| `CONTENT_SERVICE_URL` | api-gateway, user-service | Upstream content-service URL (user-service uses it for service-to-service subscription-count updates) |
| `SERVICE_CLIENT_SECRET` | user-service | Secret for the `client_credentials` machine token user-service uses to call content-service's subscribe/unsubscribe endpoints (scope `source.write`). If unset, those endpoints are not registered and subscriber-count sync no-ops (the `dev` profile sets a default). |
| `SERVICE_AUTH_TOKEN_URI` | user-service | Token endpoint user-service calls for the machine token (defaults to its own `http://localhost:8081/oauth2/token`) |
| `CORS_ALLOWED_ORIGINS` | api-gateway | Comma-separated allowed CORS origins. **No wildcard default** — if unset the gateway fails closed (no cross-origin allowed). Required in production; the `dev` profile sets `*`. |

## Security Model

- **JWT validation.** user-service is the OAuth2 Authorization Server; api-gateway and content-service are resource servers that validate JWTs against its JWKS (`JWT_ISSUER_URI`).
- **CORS** is configured only on api-gateway and fails closed (see `CORS_ALLOWED_ORIGINS` above).
- **Service-to-service subscriber counts.** content-service's `POST /sources/{id}/subscribe` and `/unsubscribe` require the `source.write` scope, which only user-service's `client_credentials` token carries — an ordinary end-user JWT cannot mutate the shared count or delete a source. user-service obtains that token from its own token endpoint using `SERVICE_CLIENT_SECRET`.
- **Outbound SSRF.** content-service validates user-supplied RSS URLs (`UrlSafetyValidator`) against loopback/link-local/site-local/multicast, IPv6 unique-local (`fc00::/7`), and RFC 6598 shared-address space (`100.64.0.0/10`) ranges, both at source creation and again immediately before each fetch. The fetch-time validation also pins the connection to exactly the addresses just validated (`PinnedDnsResolverProvider`, a JVM-wide `InetAddressResolverProvider`, JEP 418), closing a DNS-rebinding TOCTOU gap where the actual HTTP connection would otherwise re-resolve the hostname independently a moment later.

## OpenAPI (Code-First)

The controllers are the source of truth. springdoc generates per-service specs during tests (`OpenApiDocGenerationTest`), which are merged into `api/openapi.yaml` by `api/scripts/gen-all.sh`. Consumer clients (Python, TypeScript) are generated from that spec — no Java server stubs are generated. See [OpenAPI Workflow](../../openapi-workflow.md).

## Testing

```bash
./gradlew test                       # all modules
./gradlew :user-service:test         # single module
```

Tests use `@WebMvcTest` for controller-level tests, `@ExtendWith(MockitoExtension.class)` for service-layer unit tests, and `@SpringBootTest` with Testcontainers for integration smoke tests (health, registration, login).
