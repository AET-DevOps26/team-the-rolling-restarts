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
- CI/CD deploy guide: `docs/cicd-azure-deploy.md` (GitHub Actions build → push to ACR → deploy to the VM)
- `docker-compose.azure.yaml` - override that runs the stack from ACR images instead of local builds
- Helper tasks: `Makefile` (`make terraform-plan`, `make deploy-azure`)

---

# Local Docker Setup

This section describes the multi-service Docker setup for local development and testing.

## Prerequisites

- Docker Engine with Docker Compose v2.24+ (the dev override uses the Compose `!reset` merge tag)

## Services in the stack

- `reverse-proxy` (nginx) on `8080` — single entry point; routes `/api`, `/actuator`, swagger to the gateway and everything else to the web client
- `web-client` (Next.js) on `3000`
- `api-gateway` (Spring Boot, Spring Cloud Gateway) — internal `8080`
- `user-service` (Spring Boot, OAuth2 Authorization Server, MongoDB) on `8081`
- `content-service` (Spring Boot, MongoDB) on `8082`
- `gen-ai` (FastAPI) on `8000`
- `mongodb` on `27017` — shared instance, separate databases (`users`, `content`)
- `grafana-lgtm` (Grafana + OTEL collector) on `3001`, `4317`, `4318`

All services run on one project with two Docker networks:

- `frontend` network: `reverse-proxy` <-> `web-client` <-> `api-gateway`
- `backend` network: `api-gateway` <-> `user-service` <-> `content-service` <-> `gen-ai` <-> `mongodb` <-> observability

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

- `NEXT_PUBLIC_API_BASE_URL` - API base URL for the web client (defaults to `http://localhost:8080`)
- `APP_PORT` - host port for the nginx reverse proxy / single entry point (defaults to `8080`)
- `LLM_PROVIDER`, `LLM_API_KEY`, `LLM_MODEL`, `LOG_LEVEL` - GenAI service settings
- `MONGO_*` - MongoDB credentials and port
- `WATCHPACK_POLLING` - optional file-watch compatibility flag for local Linux/VM setups

## Start all major components (single command)

From repository root:

```bash
docker compose --env-file infra/.env -f infra/docker-compose.yaml -f infra/docker-compose.dev.yaml up --build
```

This command starts and connects client, gateway, user-service, content-service, GenAI, and supporting services together.

## Test workflow

Run dependencies once, then execute test containers sequentially:

```bash
docker compose --env-file infra/.env -f infra/docker-compose.yaml -f infra/docker-compose.test.yaml up -d mongodb gen-ai
docker compose --env-file infra/.env -f infra/docker-compose.yaml -f infra/docker-compose.test.yaml run --rm spring-test
docker compose --env-file infra/.env -f infra/docker-compose.yaml -f infra/docker-compose.test.yaml run --rm gen-ai-test
docker compose --env-file infra/.env -f infra/docker-compose.yaml -f infra/docker-compose.test.yaml run --rm web-client-test
```

The `spring-test` container runs `./gradlew test` across all Spring subprojects (api-gateway, user-service, content-service) in a single pass.

This avoids premature shutdown from `--abort-on-container-exit` and keeps test execution deterministic.

In `docker-compose.test.yaml`, base app services (`web-client`, `api-gateway`, `user-service`, `content-service`, `grafana-lgtm`) are assigned a non-test profile (`manual`) so `--profile test` does not start them by default.

When the run finishes, clean up test dependencies:

```bash
docker compose --env-file infra/.env -f infra/docker-compose.yaml -f infra/docker-compose.test.yaml down -v
```

## Service dependencies

- `reverse-proxy` depends on healthy `api-gateway` and a started `web-client`
- `api-gateway` depends on `user-service` and `content-service`
- `user-service` and `content-service` each depend on healthy `mongodb`
- `web-client` waits for `api-gateway` to start
- test containers depend on the same base services to ensure integration-like behavior

## Useful commands

```bash
# Stop dev stack and remove containers
docker compose --env-file infra/.env -f infra/docker-compose.yaml -f infra/docker-compose.dev.yaml down

# View merged compose config for debugging
docker compose --env-file infra/.env -f infra/docker-compose.yaml -f infra/docker-compose.dev.yaml config
```
