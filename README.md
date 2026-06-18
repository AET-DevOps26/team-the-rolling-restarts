# team-the-rolling-restarts
Repository for team The Rolling Restarts

## Architecture

The Personalised News Aggregator is composed of the following services:

| Service            | Stack                                              | Port | Purpose                                                             |
| ------------------ | -------------------------------------------------- | ---- | ------------------------------------------------------------------- |
| `web-client`       | Next.js, React 19, TypeScript                      | 3000 | Frontend UI                                                         |
| `api-gateway`      | Spring Boot, Spring Cloud Gateway                  | 8080 | Routes traffic to microservices, JWT validation, unified Swagger UI  |
| `user-service`     | Spring Boot, Spring Security OAuth2 AS, PostgreSQL | 8081 | Authentication, user profiles, settings                             |
| `content-service`  | Spring Boot, Spring Data MongoDB                   | 8082 | RSS feed management, article storage, scheduled fetching            |
| `gen-ai`           | Python, FastAPI, LangChain                         | 8000 | AI-powered summaries, explanations, sentiment                       |

The three Spring Boot services (`api-gateway`, `user-service`, `content-service`) live in a single Gradle multi-module project at `services/spring/`, sharing generated OpenAPI code via a `generated` library subproject.

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
│   │   ├── generated/      Shared OpenAPI-generated code
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
- End-to-end runbook: `docs/azure-vm-deployment.md`
- Optional helper commands: `Makefile` (`make terraform-plan`, `make deploy-azure`)
