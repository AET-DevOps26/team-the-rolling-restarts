# Secrets & Environment Variables Reference

Every secret and configurable variable across all deployment targets, with example values.

> **Rule:** Never commit real credentials. Use the `.example` files as templates and keep actual values in untracked files (`.env`, `secrets-values.yaml`, `all.yml`).

---

## Docker Compose (Local Dev)

**File:** `infra/.env` (copy from `infra/.env.example`) — the Makefile reads from it automatically for all commands.

### Secrets

These must be changed from defaults in production. The dev defaults below work out of the box for local development.

| Variable | Dev default | Prod guidance | Used by |
| -------- | ----------- | ------------- | ------- |
| `MONGO_ROOT_PASSWORD` | `secret` | Strong random password (24+ chars) | mongodb, user-service, content-service |
| `MONGO_ROOT_USERNAME` | `root` | Keep or change | mongodb, user-service, content-service |
| `JWT_RSA_PUBLIC_KEY` / `JWT_RSA_PRIVATE_KEY` | Committed dev key pair (in `docker-compose.dev.yaml`) | Generate a fresh RSA pair, inject via secret store | user-service (JWT signing) |
| `SERVICE_CLIENT_SECRET` | `dev-service-secret` (in `docker-compose.dev.yaml`) | Strong random value (`openssl rand -hex 32`) | user-service → content-service subscribe/unsubscribe (client_credentials, scope `source.write`) |
| `LLM_API_KEY` | _(empty)_ | Logos API key (`lg-...`, from tutor); TUM network / eduVPN only | gen-ai |
| `GRAFANA_ADMIN_PASSWORD` | `admin` | Strong random password (`openssl rand -hex 16`) | grafana-lgtm admin login (also reachable at `/monitoring` via the reverse proxy, not just `LGTM_GRAFANA_PORT` directly) |

### Configuration

Safe to leave at defaults for local dev. Override as needed.

| Variable | Dev default | Description |
| -------- | ----------- | ----------- |
| `REGISTRY` | `ghcr.io/aet-devops26/team-the-rolling-restarts` | Container image registry. Use `ghcr.io/<github-username>/rolling-restarts` for personal dev |
| `IMAGE_TAG` | `latest` | Image tag. Makefile defaults to current commit SHA |
| `API_BASE_URL` | `http://localhost:8080` | Gateway base URL for the "test" compose profile (`infra/docker-compose.yaml` itself hardcodes the correct value per deployment target directly, not from this file — see `web-client/src/lib/api/client.ts`) |
| `APP_PORT` | `8080` (local) / `80` (VM) | Host port for the nginx reverse proxy — the single entry point; the web client is served through it at `/`, not on its own port |
| `GEN_AI_PORT` | `8000` | Host port for GenAI service |
| `LLM_PROVIDER` | `logos` | LLM provider (`logos` for cloud, `ollama` for local) |
| `LLM_BASE_URL` | `https://logos.aet.cit.tum.de/v1` | Logos OpenAI-compatible endpoint (TUM network / eduVPN only) |
| `LLM_MODEL` | `openai/gpt-oss-120b` | Model name (Logos default; use an Ollama model name when `LLM_PROVIDER=ollama`) |
| `INTERNAL_API_URL` | `http://api-gateway:8080` | In-network URL gen-ai uses to fetch articles via the gateway |
| `OLLAMA_BASE_URL` | `http://ollama:11434` | Ollama API URL (when `LLM_PROVIDER=ollama` + compose profile `local-llm`) |
| `OLLAMA_PORT` | `11434` | Host port for Ollama (only with `--profile local-llm`) |
| `LOG_LEVEL` | `INFO` | GenAI log level |
| `MONGO_PORT` | `27017` | Host port for MongoDB |
| `MONGO_DATABASE` | `mydatabase` | MongoDB init database name |
| `LGTM_GRAFANA_PORT` | `3001` | Host port for Grafana |
| `LGTM_OTLP_GRPC_PORT` | `4317` | Host port for OTLP gRPC |
| `LGTM_OTLP_HTTP_PORT` | `4318` | Host port for OTLP HTTP |
| `WATCHPACK_POLLING` | `false` | Set `true` on some Linux setups for file watching |

### Example `infra/.env` (dev)

```bash
# Works out of the box for local development
API_BASE_URL=http://localhost:8080
APP_PORT=8080
GEN_AI_PORT=8000

LLM_PROVIDER=logos
LLM_BASE_URL=https://logos.aet.cit.tum.de/v1
LLM_API_KEY=lg-your-logos-key-here
LLM_MODEL=openai/gpt-oss-120b
INTERNAL_API_URL=http://api-gateway:8080
OLLAMA_BASE_URL=http://ollama:11434
LOG_LEVEL=INFO

MONGO_PORT=27017
MONGO_DATABASE=mydatabase
MONGO_ROOT_USERNAME=root
MONGO_ROOT_PASSWORD=secret

LGTM_GRAFANA_PORT=3001
LGTM_OTLP_GRPC_PORT=4317
LGTM_OTLP_HTTP_PORT=4318
GRAFANA_ADMIN_PASSWORD=your-strong-password-here
WATCHPACK_POLLING=false
```

---

## Azure VM Deployment

**File:** `infra/ansible/group_vars/all.yml` (copy from `all.yml.example`)

All the same variables as Docker Compose, passed via `app_env`. The Ansible playbook templates them into the `.env` file on the VM.

> **Azure VM is off the TUM network** — Logos (`https://logos.aet.cit.tum.de/v1`) is not reachable from the VM. Use `LLM_PROVIDER=ollama`, enable the Compose profile `local-llm` (starts an Ollama container), pull a model, and set `LLM_MODEL` to that model name. Alternatively, run gen-ai locally on eduVPN with `LLM_PROVIDER=logos`.

### Additional Ansible variables

| Variable | Example | Description |
| -------- | ------- | ----------- |
| `project_root` | `/opt/rolling-restarts` | App install directory on the VM |
| `registry` | `ghcr.io/aet-devops26/team-the-rolling-restarts` | Container image registry. Use `ghcr.io/<github-username>/rolling-restarts` for personal dev |
| `image_tag` | `latest` | Image tag (overridden by `make ansible-deploy IMAGE_TAG=...`) |
| `registry_user` | `""` | GHCR username (for private registries) |
| `registry_token` | `""` | GHCR PAT with `read:packages` (for private registries) |

### Example `infra/ansible/group_vars/all.yml` (prod)

```yaml
---
project_root: /opt/rolling-restarts

registry: ghcr.io/aet-devops26/team-the-rolling-restarts
image_tag: latest

app_env:
  APP_PORT: "8080"
  GEN_AI_PORT: "8000"

  LLM_PROVIDER: "ollama"
  LLM_BASE_URL: "https://logos.aet.cit.tum.de/v1"
  LLM_API_KEY: ""
  LLM_MODEL: "llama3.2"
  INTERNAL_API_URL: "http://api-gateway:8080"
  OLLAMA_BASE_URL: "http://ollama:11434"
  OLLAMA_PORT: "11434"
  LOG_LEVEL: "INFO"

  MONGO_PORT: "27017"
  MONGO_DATABASE: "mydatabase"
  MONGO_ROOT_USERNAME: "root"
  MONGO_ROOT_PASSWORD: "CHANGE-ME-strong-random"  # <-- real secret

  LGTM_GRAFANA_PORT: "3001"
  LGTM_OTLP_GRPC_PORT: "4317"
  LGTM_OTLP_HTTP_PORT: "4318"
  GRAFANA_ADMIN_PASSWORD: "CHANGE-ME-strong-random"  # <-- real secret
```

---

## Kubernetes / Helm

Helm uses multiple values files layered together: `values.yaml` (base config, checked in), `values-prod.yaml` (prod overrides, checked in), `values-dev.yaml` (dev/manual-deploy overrides, checked in), `secrets-values.yaml` (credentials, not checked in), and `image-values.yaml` (image tags, set by CI).

**File:** `infra/helm/secrets-values.yaml` (copy from `secrets-values.example.yaml`)

### Secrets (`secrets-values.yaml`)

These are **required** — `values.yaml` ships them as empty strings and `templates/secrets.yaml`
wraps each in Helm's `required` function, so `helm` refuses to render unless `secrets-values.yaml`
provides every one. No credential material is committed to the chart.

Generate a working file in one command with `make helm-secrets` (fresh RSA pair + random Mongo
password), or copy `secrets-values.example.yaml` and fill it in by hand.

| Key | Required | Prod guidance | Consumed by |
| --- | -------- | ------------- | ----------- |
| `mongodb.rootUsername` | yes | Keep `root` or change | mongodb + both `mongodb-credentials` and `mongodb-user-credentials` Secrets |
| `mongodb.rootPassword` | yes | Strong random password | mongodb + both Secrets |
| `userService.jwtKeys.publicKey` / `userService.jwtKeys.privateKey` | yes | Fresh RSA pair | `jwt-keys` Secret → user-service JWT signing |
| `userService.serviceClientSecret` | yes | Strong random value (`openssl rand -hex 32`) | `service-credentials` Secret → user-service's client_credentials token for content-service subscribe/unsubscribe |
| `genAi.llmApiKey` | no | Logos API key (`lg-...`); TUM network / eduVPN only | `llm-credentials` Secret → gen-ai cloud LLM calls |
| `monitoring.adminPassword` | yes | Strong random password (`openssl rand -hex 16`) | `grafana-admin-credentials` Secret (in `monitoring.namespace`) → grafana-lgtm admin login, reachable at `/monitoring` via the shared ingress |

> **Rotating `mongodb.rootPassword`:** MongoDB only applies the root password on first init (empty
> data dir). Changing it while the `mongodb-data` PVC still exists leaves the old password in place
> and the services fail with "Authentication failed". Wipe the volume to re-initialize:
> `kubectl -n deployment delete deploy mongodb && kubectl -n deployment delete pvc mongodb-data`,
> then redeploy.
>
> **Rotating `monitoring.adminPassword`:** same class of gotcha — Grafana only applies
> `GF_SECURITY_ADMIN_PASSWORD` on first boot against an empty `grafana-lgtm-data` PVC. Changing the
> value and redeploying does **not** change an existing admin password; rotate it in place instead
> with `kubectl exec -n monitoring-rolling-restarts deploy/grafana-lgtm -- sh -c 'cd
> /otel-lgtm/grafana && GF_PATHS_HOME=/data/grafana GF_PATHS_DATA=/data/grafana/data
> GF_PATHS_PLUGINS=/data/grafana/plugins ./bin/grafana cli admin reset-admin-password
> <new-password>'` (see `docs/internal/07-gotchas.md`).

Both services share the same MongoDB instance with data isolation via separate databases:

- **content-service** connects to database `content` via the `mongodb-credentials` Secret
- **user-service** connects to database `users` via the `mongodb-user-credentials` Secret

### Configuration (`values.yaml` — checked in)

These are set directly in `values.yaml` or overridden per-environment.

| Key | Dev value | Prod override | Description |
| --- | --------- | ------------- | ----------- |
| `global.replicas` | `1` | `2` (via `values-prod.yaml`) | Pod replica count |
| `global.tag` | `latest` | Commit SHA (via `image-values.yaml`) | Container image tag |
| `apiGateway.env` `CORS_ALLOWED_ORIGINS` | `https://rolling-restarts...` | Your domain | Allowed CORS origins |
| `apiGateway.env` `JWT_ISSUER_URI` | `http://user-service:8081` | Keep (cluster-internal) | JWT issuer for token validation |
| `ingress.clusterIssuer` | `letsencrypt-staging` | `letsencrypt-prod` (via `values-prod.yaml`) | TLS certificate issuer |
| `host` | `rolling-restarts.stud...` | Your domain | Ingress hostname (path-based routing) |

### Example `infra/helm/secrets-values.yaml` (prod)

All four keys are mandatory — see `secrets-values.example.yaml` for the full template, or run
`make helm-secrets` to generate this automatically.

```yaml
mongodb:
  rootUsername: root
  rootPassword: "a-strong-random-password-here"
userService:
  jwtKeys:
    publicKey: |
      -----BEGIN PUBLIC KEY-----
      ...
      -----END PUBLIC KEY-----
    privateKey: |
      -----BEGIN PRIVATE KEY-----
      ...
      -----END PRIVATE KEY-----
  serviceClientSecret: "a-strong-random-value-here"
```

### Helm values file layering

```text
values.yaml                 # Base config (checked in)
├── values-prod.yaml        # Prod overrides: replicas=2, letsencrypt-prod (checked in)
├── values-dev.yaml         # Dev overrides: dev. host prefix, 1 replica (checked in; ENV=dev path)
├── secrets-values.yaml     # Database credentials (NOT checked in)
└── image-values.yaml       # Container image tags, set by CI (checked in)
```

**Dev deploy:** `make helm-deploy`
**Prod deploy:** `make helm-deploy ENV=prod`

The Makefile auto-detects `secrets-values.yaml` when present.

---

## Spring Profiles

The Spring services use profiles to switch between dev and production configuration. The profile is selected by `SPRING_PROFILES_ACTIVE` (set in Docker Compose or Helm `values.yaml`).

| Profile | How activated | What it does |
| ------- | ------------- | ------------ |
| `dev` | `SPRING_PROFILES_ACTIVE=dev` or default in IDE | Hardcoded local MongoDB URIs, debug logging, all actuator endpoints exposed |
| `production` | Set in Helm `values.yaml` and Docker Compose | No hardcoded credentials (env vars required), graceful shutdown, minimal actuator endpoints |
| _(base)_ | Always active | Port config, gateway routes, JWT issuer URI — all read from env vars with sensible fallbacks |

### Env vars required per service (production profile)

| Service | Required env vars |
| ------- | ----------------- |
| **api-gateway** | `CORS_ALLOWED_ORIGINS`, `JWT_ISSUER_URI`, `USER_SERVICE_URL`, `CONTENT_SERVICE_URL` |
| **user-service** | `SPRING_MONGODB_URI`, `JWT_ISSUER`, `JWT_RSA_PUBLIC_KEY`, `JWT_RSA_PRIVATE_KEY`, `CONTENT_SERVICE_URL`, `SERVICE_CLIENT_SECRET` |
| **content-service** | `SPRING_MONGODB_URI`, `JWT_ISSUER_URI` |
| **gen-ai** | `LLM_PROVIDER`, `LLM_BASE_URL`, `LLM_MODEL`, `INTERNAL_API_URL`; `LLM_API_KEY` optional (Logos key, TUM network / eduVPN only); `OLLAMA_BASE_URL` when `LLM_PROVIDER=ollama` |

> **JWT issuer must match across services.** user-service stamps the `iss` claim on tokens from `JWT_ISSUER` and serves OIDC discovery at that URL. api-gateway and content-service validate tokens via `JWT_ISSUER_URI`, which must resolve to the same user-service URL (default `http://user-service:8081` in Compose/Helm). A mismatch causes resource servers to reject every token with 401.
>
> **JWT signing key must be shared across user-service replicas.** user-service signs tokens
> with the RSA key from `JWT_RSA_PUBLIC_KEY` / `JWT_RSA_PRIVATE_KEY` and derives a deterministic
> JWK `kid` from its thumbprint. Every replica therefore advertises an identical JWKS, so a token
> signed by one pod validates against the key served by any pod behind the Service — this is what
> makes user-service horizontally scalable (`replicas: 2`). If the keys are **unset**, each pod
> generates its own ephemeral key (logged as a warning) and auth breaks intermittently with 2+
> replicas. All replicas must receive the **same** key pair (a single `jwt-keys` Secret in
> Kubernetes, one env value in Compose).

---

## Quick Checklist

### Before `make compose-up` (local dev)

- [ ] `cp infra/.env.example infra/.env`
- [ ] Set `LLM_API_KEY` (Logos `lg-...` key) if on TUM network / eduVPN and you want cloud LLM
- [ ] Or set `LLM_PROVIDER=ollama` and run `make compose-up` with `--profile local-llm` for local model

### Before `make deploy-azure`

- [ ] `cp infra/ansible/group_vars/all.yml.example infra/ansible/group_vars/all.yml`
- [ ] Set a real password for `MONGO_ROOT_PASSWORD`
- [ ] Set `LLM_PROVIDER=ollama` (Azure VM is off TUM network — Logos unreachable); use compose profile `local-llm`
- [ ] Set `project_repo_url` to your fork/org

### Before `make helm-deploy`

- [ ] `cp infra/helm/secrets-values.example.yaml infra/helm/secrets-values.yaml`
- [ ] Set a real password for `mongodb.rootPassword`
- [ ] Optionally set `genAi.llmApiKey` for Logos cloud LLM (in-cluster TUM network only)
- [ ] For prod: `make helm-deploy ENV=prod`
