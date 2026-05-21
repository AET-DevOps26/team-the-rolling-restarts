# Local Docker Setup

This folder contains a multi-service Docker setup for local development and testing.

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

- `NEXT_PUBLIC_API_BASE_URL` - URL used by browser-side client calls (defaults to `http://localhost:8080`)
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

Run stack dependencies and test containers in one command:

```bash
docker compose --env-file infra/.env -f infra/docker-compose.yaml -f infra/docker-compose.test.yaml --profile test up --build --abort-on-container-exit
```

When the run finishes, clean up:

```bash
docker compose --env-file infra/.env -f infra/docker-compose.yaml -f infra/docker-compose.test.yaml --profile test down -v
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
