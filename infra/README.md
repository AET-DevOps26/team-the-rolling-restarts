# Infrastructure

This folder contains both local Docker setup and cloud automation assets.

## Contents

- `docker-compose*.yaml` - local/test container orchestration
- `terraform/azure-vm` - Azure VM provisioning (Terraform)
- `ansible` - VM configuration and project deployment (Ansible)
- `scripts/generate-ansible-inventory.sh` - build Ansible inventory from Terraform outputs

## Azure automation quick links

- Terraform guide: `infra/terraform/azure-vm/README.md`
- Ansible guide: `infra/ansible/README.md`
- Helper tasks: `Makefile` (`make terraform-plan`, `make deploy-azure`)

---

# Local Docker Setup

This section describes the multi-service Docker setup for local development and testing.

## Prerequisites

- Docker Engine with Docker Compose v2.24+ (the dev override uses the Compose `!reset` merge tag)

## Services in the stack

- `web-client` (Next.js) on `3000`
- `spring-api` (Spring Boot) on `8080`
- `gen-ai` (FastAPI) on `8000`
- `mongodb` on `27017`
- `postgres` on `5432`
- `grafana-lgtm` (Grafana + OTEL collector) on `3001`, `4317`, `4318`

All services run on one project with two Docker networks:

- `frontend` network: `web-client` <-> `spring-api`
- `backend` network: `spring-api` <-> `gen-ai` <-> databases <-> observability

## Compose files

- `docker-compose.yaml` - base stack (production-like container runtime)
- `docker-compose.dev.yaml` - development overrides (source mounts, hot reload/dev run commands)
- `docker-compose.test.yaml` - test workflow services (`--profile test`)

## Environment variables

Copy `.env.example` and customize values:

```bash
cp infra/.env.example infra/.env
```

Main variables:

- `NEXT_PUBLIC_API_BASE_URL` - reserved for upcoming API integration; current client pages use mock data (defaults to `http://localhost:8080`)
- `LLM_PROVIDER`, `LLM_API_KEY`, `LLM_MODEL`, `LOG_LEVEL` - GenAI service settings
- `MONGO_*` and `POSTGRES_*` - database credentials and ports
- `WATCHPACK_POLLING` - optional file-watch compatibility flag for local Linux/VM setups

## Start all major components (single command)

From repository root:

```bash
docker compose --env-file infra/.env -f infra/docker-compose.yaml -f infra/docker-compose.dev.yaml up --build
```

This command starts and connects client, API, GenAI, and supporting services together.

## Test workflow

Run dependencies once, then execute test containers sequentially:

```bash
docker compose --env-file infra/.env -f infra/docker-compose.yaml -f infra/docker-compose.test.yaml up -d mongodb postgres gen-ai
docker compose --env-file infra/.env -f infra/docker-compose.yaml -f infra/docker-compose.test.yaml run --rm spring-api-test
docker compose --env-file infra/.env -f infra/docker-compose.yaml -f infra/docker-compose.test.yaml run --rm gen-ai-test
docker compose --env-file infra/.env -f infra/docker-compose.yaml -f infra/docker-compose.test.yaml run --rm web-client-test
```

This avoids premature shutdown from `--abort-on-container-exit` and keeps test execution deterministic.

In `docker-compose.test.yaml`, base app services (`web-client`, `spring-api`, `grafana-lgtm`) are assigned a non-test profile (`manual`) so `--profile test` does not start them by default.

When the run finishes, clean up test dependencies:

```bash
docker compose --env-file infra/.env -f infra/docker-compose.yaml -f infra/docker-compose.test.yaml down -v
```

## Service dependencies

- `spring-api` depends on healthy `mongodb`, `postgres`, and `gen-ai`
- `web-client` waits for `spring-api` to start
- test containers depend on the same base services to ensure integration-like behavior

## Useful commands

```bash
# Stop dev stack and remove containers
docker compose --env-file infra/.env -f infra/docker-compose.yaml -f infra/docker-compose.dev.yaml down

# View merged compose config for debugging
docker compose --env-file infra/.env -f infra/docker-compose.yaml -f infra/docker-compose.dev.yaml config
```
