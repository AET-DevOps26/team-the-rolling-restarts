# team-the-rolling-restarts
Repository for team The Rolling Restarts

## Architecture

The Personalised News Aggregator is composed of the following services:

| Service            | Stack                                              | Port | Purpose                                                             |
| ------------------ | -------------------------------------------------- | ---- | ------------------------------------------------------------------- |
| `web-client`       | Next.js, React 19, TypeScript                      | 3000 | Frontend UI                                                         |
| `api-gateway`      | Spring Boot, Spring Cloud Gateway                  | 8080 | Routes traffic to microservices, JWT validation, unified Swagger UI  |
| `user-service`     | Spring Boot, Spring Security OAuth2 AS, MongoDB    | 8081 | Authentication, user profiles, settings                             |
| `content-service`  | Spring Boot, Spring Data MongoDB                   | 8082 | RSS feed management, article storage, scheduled fetching            |
| `gen-ai`           | Python, FastAPI, LangChain                         | 8000 | AI-powered summaries, explanations, sentiment                       |

The three Spring Boot services (`api-gateway`, `user-service`, `content-service`) live in a single Gradle multi-module project at `services/spring/`. The API is **code-first**: the controllers are the source of truth, springdoc derives `api/openapi.yaml` from them, and the web/gen-ai clients are generated from that contract. See [docs: OpenAPI workflow](docs/source/openapi-workflow.md).

## Quick start

```bash
cp infra/.env.example infra/.env    # fill in required values
make compose-up                     # start the local dev stack
make smoke-test                     # verify endpoints
```

See [docs/source/deployment-testing.md](docs/source/deployment-testing.md) for the full local
verification checklist, and [docs/source/secrets-reference.md](docs/source/secrets-reference.md)
for all configuration options. For day-to-day development:

```bash
make generate       # regenerate OpenAPI spec + consumer clients
make spring-build   # compile and test Spring services
make preflight      # full pre-flight: generate, build, lint helm, validate terraform
```

Full docs (all guides below, rendered): <https://aet-devops26.github.io/team-the-rolling-restarts/>

## API documentation

The OpenAPI contract (`api/openapi.yaml`) is generated from the Spring controllers — see
[docs/source/openapi-workflow.md](docs/source/openapi-workflow.md) for the code-first pipeline.
Once the stack is running:

- Swagger UI (aggregated across all services, via the gateway): `http://localhost:8080/swagger-ui.html`
- Raw OpenAPI spec: `http://localhost:8080/v3/api-docs`
- Static rendered reference: [API Docs](https://aet-devops26.github.io/team-the-rolling-restarts/api.html)
  (built by [publish_docs.yml](.github/workflows/publish_docs.yml) from `api/openapi.yaml` on every deploy)

## Project layout

```text
├── api/                    OpenAPI spec + generation scripts
├── docs/                   MkDocs documentation
├── infra/
│   ├── docker-compose*.yaml
│   ├── helm/               Helm chart for Kubernetes
│   ├── k8s/                Raw Kubernetes manifests
│   ├── terraform/          Azure VM provisioning
│   └── ansible/            VM configuration and deployment
├── services/
│   ├── spring/             Multi-module Gradle project
│   │   ├── api-gateway/    API gateway service
│   │   ├── user-service/   User & auth service
│   │   └── content-service/ Content & RSS service
│   └── gen-ai/             GenAI Python service
├── web-client/             Next.js frontend
└── Makefile                Helper commands
```

## Infrastructure automation

- Terraform for Azure VM provisioning: `infra/terraform/azure-vm`
- Ansible for VM configuration and deployment: `infra/ansible`
- End-to-end runbook: `docs/source/azure-vm-deployment.md`
- Optional helper commands: `Makefile` (`make terraform-plan`, `make deploy-azure`)

## CI/CD

All workflows live in `.github/workflows/`; every push and PR runs `ci.yml` (build + test all
services, regenerate and lint the OpenAPI contract, `terraform validate`, `helm lint`). Merges to
`main` additionally trigger:

| Workflow | Triggers on | Does |
| --- | --- | --- |
| [`upload_images.yml`](.github/workflows/upload_images.yml) (`build-and-package`) | every push | Builds & pushes multi-arch Docker images to GHCR |
| [`deploy_kubernetes.yml`](.github/workflows/deploy_kubernetes.yml) | `build-and-package` succeeding on `main`, or manual dispatch | `helm upgrade --install` to the Kubernetes cluster — see [Kubernetes CD Pipeline](docs/source/cicd-kubernetes-deploy.md) |
| [`deploy_monitoring.yml`](.github/workflows/deploy_monitoring.yml) | push touching monitoring files, or manual dispatch | Lighter redeploy of just the Grafana/Prometheus stack |
| [`deploy-azure.yml`](.github/workflows/deploy-azure.yml) | push to `main`, or manual dispatch | Builds images to ACR, deploys to the Azure VM via `az vm run-command` — see [Azure CD Pipeline](docs/source/cicd-azure-deploy.md) |
| [`publish_docs.yml`](.github/workflows/publish_docs.yml) | push to `main`/`dev`, or manual dispatch | Renders PlantUML diagrams + OpenAPI reference, builds and deploys this documentation site to GitHub Pages |

Security scanning (`gitleaks`, `hadolint`, `kics`, `zizmor`, `typos`, `npm audit`, `trivy`,
`dockle`) runs locally via `make security-scan` — see
[docs/source/security-scanning.md](docs/source/security-scanning.md).

## Monitoring

Metrics, dashboards, and alerts run on a bundled Prometheus + Grafana stack
(`grafana/otel-lgtm`). See [docs/source/monitoring.md](docs/source/monitoring.md) for how to
access Grafana and what's provisioned in each deployment target.

## Team & responsibilities

This team has 2 active members (originally 3 — the third member left the project). Each member
owns a primary subsystem but collaborates across boundaries for integration, deployment, and
debugging, per the course's team model — see
[docs/requirements/01-team-and-collaboration.md](docs/requirements/01-team-and-collaboration.md).
Enforced via [`.github/CODEOWNERS`](.github/CODEOWNERS):

| Member | GitHub | Owns |
| --- | --- | --- |
| Baris Can | [`brscn2`](https://github.com/brscn2) | `web-client`, `gen-ai` (joint) |
| Yi Rui Cui | [`YRC99`](https://github.com/YRC99) | `services/spring`, `gen-ai` (joint) |

Infra, CI/CD, the API contract, and docs (`infra/`, `.github/`, `api/`, `docs/`) are jointly owned
by both members.
