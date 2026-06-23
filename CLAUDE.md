# CLAUDE.md

## Project Overview

Personalised News Aggregator with GenAI — a microservices platform combining Spring Boot backends, a Next.js frontend, and a Python GenAI service.

## Repository Layout

```
api/                    # OpenAPI spec (single source of truth)
services/
  spring/               # Gradle multi-module: api-gateway, user-service, content-service, generated
  gen-ai/               # Python FastAPI + LangChain
web-client/             # Next.js + React 19 + TypeScript
infra/
  docker-compose*.yaml  # Local orchestration
  ansible/              # VM deployment (roles: common, docker, app)
  terraform/azure-vm/   # Azure VM provisioning
  helm/                 # Kubernetes Helm chart
  k8s/                  # Raw Kubernetes manifests
docs/                   # MkDocs documentation
```

## Build & Test Commands

```bash
# Spring services (from services/spring/)
./gradlew build                    # compile + test all
./gradlew :api-gateway:bootRun     # run single service

# Web client (from web-client/)
npm ci && npm run dev              # local dev server

# GenAI (from services/gen-ai/)
pip install -e ".[dev]" && pytest  # test

# OpenAPI code generation (from project root)
./api/scripts/gen-all.sh

# Helm chart (from infra/helm/)
helm lint .
helm template test .

# Docker Compose (from project root)
docker compose --env-file infra/.env -f infra/docker-compose.yaml -f infra/docker-compose.dev.yaml up --build
```

## Key Conventions

- **Code-first OpenAPI**: The Spring controllers are the source of truth. `api/openapi.yaml` is *generated* from them (springdoc per service → merged with gateway prefixes via `api/scripts/gen-all.sh`); DTOs are hand-written records, not generated. Consumer clients (web-client TypeScript, gen-ai Python) are generated *from* that spec — no Java server stubs are generated. See [docs/source/openapi-workflow.md](docs/source/openapi-workflow.md).
- **Spring profiles**: `dev` for local (has default credentials), `production` for deployed (requires env vars). Base properties have no hardcoded secrets.
- **JWT auth**: user-service is the OAuth2 Authorization Server. api-gateway and content-service validate JWTs as resource servers.
- **CORS**: Configured only on api-gateway via `CORS_ALLOWED_ORIGINS` env var.
- **Unified error schema**: All services use `{timestamp, code, message, details, path}` via `@RestControllerAdvice`.

## Deployment Paths

1. **Local**: Docker Compose (`infra/docker-compose.yaml` + `docker-compose.dev.yaml`)
2. **Azure VM**: Terraform → Ansible → Docker Compose (see `docs/source/azure-vm-deployment.md`)
3. **Kubernetes**: Helm chart (`infra/helm/`) or raw manifests (`infra/k8s/`)
