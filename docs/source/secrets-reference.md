# Secrets & Environment Variables Reference

Every secret and configurable variable across all deployment targets, with example values.

> **Rule:** Never commit real credentials. Use the `.example` files as templates and keep actual values in untracked files (`.env`, `secrets-values.yaml`, `all.yml`).

---

## Docker Compose (Local Dev)

**File:** `infra/.env` (copy from `infra/.env.example`)

### Secrets

These must be changed from defaults in production. The dev defaults below work out of the box for local development.

| Variable | Dev default | Prod guidance | Used by |
| -------- | ----------- | ------------- | ------- |
| `MONGO_ROOT_PASSWORD` | `secret` | Strong random password (24+ chars) | mongodb, user-service, content-service |
| `MONGO_ROOT_USERNAME` | `root` | Keep or change | mongodb, user-service, content-service |
| `LLM_API_KEY` | _(empty)_ | OpenAI / provider API key | gen-ai |

### Configuration

Safe to leave at defaults for local dev. Override as needed.

| Variable | Dev default | Description |
| -------- | ----------- | ----------- |
| `NEXT_PUBLIC_API_BASE_URL` | `http://localhost:8080` | API base URL baked into web client at build time |
| `WEB_CLIENT_PORT` | `3000` | Host port for web client |
| `SPRING_API_PORT` | `8080` | Host port for API gateway |
| `GEN_AI_PORT` | `8000` | Host port for GenAI service |
| `LLM_PROVIDER` | `openai` | LLM provider (`openai`, etc.) |
| `LLM_MODEL` | `gpt-4o-mini` | Model name |
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
NEXT_PUBLIC_API_BASE_URL=http://localhost:8080
WEB_CLIENT_PORT=3000
SPRING_API_PORT=8080
GEN_AI_PORT=8000

LLM_PROVIDER=openai
LLM_API_KEY=sk-your-openai-key-here
LLM_MODEL=gpt-4o-mini
LOG_LEVEL=INFO

MONGO_PORT=27017
MONGO_DATABASE=mydatabase
MONGO_ROOT_USERNAME=root
MONGO_ROOT_PASSWORD=secret

LGTM_GRAFANA_PORT=3001
LGTM_OTLP_GRPC_PORT=4317
LGTM_OTLP_HTTP_PORT=4318
WATCHPACK_POLLING=false
```

---

## Azure VM Deployment

**File:** `infra/ansible/group_vars/all.yml` (copy from `all.yml.example`)

All the same variables as Docker Compose, passed via `app_env`. The Ansible playbook templates them into the `.env` file on the VM.

### Additional Ansible variables

| Variable | Example | Description |
| -------- | ------- | ----------- |
| `project_root` | `/opt/rolling-restarts` | App install directory on the VM |
| `project_repo_url` | `https://github.com/org/repo.git` | Git repo to clone |
| `project_repo_version` | `main` | Branch/tag to deploy |
| `compose_file` | `infra/docker-compose.yaml` | Compose file path relative to repo root |
| `compose_env_path` | `infra/.env` | Env file path relative to repo root |

### Example `infra/ansible/group_vars/all.yml` (prod)

```yaml
---
project_root: /opt/rolling-restarts
project_repo_url: https://github.com/your-org/team-the-rolling-restarts.git
project_repo_version: main

compose_file: infra/docker-compose.yaml
compose_env_path: infra/.env

app_env:
  NEXT_PUBLIC_API_BASE_URL: "https://your-domain.com"
  WEB_CLIENT_PORT: "3000"
  SPRING_API_PORT: "8080"
  GEN_AI_PORT: "8000"

  LLM_PROVIDER: "openai"
  LLM_API_KEY: "sk-prod-key-from-vault"      # <-- real secret
  LLM_MODEL: "gpt-4o-mini"
  LOG_LEVEL: "INFO"

  MONGO_PORT: "27017"
  MONGO_DATABASE: "mydatabase"
  MONGO_ROOT_USERNAME: "root"
  MONGO_ROOT_PASSWORD: "CHANGE-ME-strong-random"  # <-- real secret

  LGTM_GRAFANA_PORT: "3001"
  LGTM_OTLP_GRPC_PORT: "4317"
  LGTM_OTLP_HTTP_PORT: "4318"
```

---

## Kubernetes / Helm

Helm uses two layers: `values.yaml` (checked in, non-secret config) and `secrets-values.yaml` (not checked in, credentials only).

**File:** `infra/helm/secrets-values.yaml` (copy from `secrets-values.example.yaml`)

### Secrets (`secrets-values.yaml`)

These override the dummy defaults in `values.yaml` and are injected into Kubernetes Secrets.

| Key | Dev default (in `values.yaml`) | Prod guidance | Consumed by |
| --- | ------------------------------ | ------------- | ----------- |
| `mongodb.rootUsername` | `root` | Keep or change | mongodb + both `mongodb-credentials` and `mongodb-user-credentials` Secrets |
| `mongodb.rootPassword` | `secret` | Strong random password | mongodb + both Secrets |

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

```yaml
mongodb:
  rootUsername: root
  rootPassword: "a-strong-random-password-here"
```

### Helm values file layering

```text
values.yaml                 # Base config (checked in)
├── values-prod.yaml        # Prod overrides: replicas=2, letsencrypt-prod (checked in)
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
| **user-service** | `SPRING_MONGODB_URI` |
| **content-service** | `SPRING_MONGODB_URI`, `JWT_ISSUER_URI` |
| **gen-ai** | `LLM_API_KEY` (optional — service starts without it but LLM calls fail) |

---

## Quick Checklist

### Before `make compose-up` (local dev)

- [ ] `cp infra/.env.example infra/.env`
- [ ] Set `LLM_API_KEY` if you want GenAI features to work

### Before `make deploy-azure`

- [ ] `cp infra/ansible/group_vars/all.yml.example infra/ansible/group_vars/all.yml`
- [ ] Set a real password for `MONGO_ROOT_PASSWORD`
- [ ] Set `LLM_API_KEY`
- [ ] Set `project_repo_url` to your fork/org

### Before `make helm-deploy`

- [ ] `cp infra/helm/secrets-values.example.yaml infra/helm/secrets-values.yaml`
- [ ] Set a real password for `mongodb.rootPassword`
- [ ] For prod: `make helm-deploy ENV=prod`
