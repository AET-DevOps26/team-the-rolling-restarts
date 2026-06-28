# Personalised News Aggregator

A microservices platform combining Spring Boot backends, a Next.js frontend, and a Python GenAI service.

## Architecture

| Service | Description |
| ------- | ----------- |
| [API Gateway](spring-services.md) | Spring Cloud Gateway MVC — routes, CORS, JWT validation, Swagger UI |
| [User Service](spring-services.md) | OAuth2 Authorization Server, user profiles, settings (MongoDB) |
| [Content Service](spring-services.md) | RSS feed management, articles, topics (MongoDB) |
| [GenAI Service](gen-ai-service.md) | Python FastAPI + LangChain — summarization, sentiment, recommendations |
| [Web Client](web-client.md) | Next.js + React 19 + TypeScript |

## Deployment paths

1. **Local** — Docker Compose ([Deployment Testing](deployment-testing.md))
2. **Azure VM (CI/CD)** — GitHub Actions → ACR → `az vm run-command` ([Azure CD Pipeline](cicd-azure-deploy.md))
3. **Azure VM (manual)** — Terraform → Ansible → Docker Compose ([Azure VM Deployment](azure-vm-deployment.md))
4. **Kubernetes (CI/CD)** — GitHub Actions → GHCR → Helm deploy ([Kubernetes CD Pipeline](cicd-kubernetes-deploy.md))
5. **Kubernetes (manual)** — Helm chart ([Helm](infra/helm/helm.md)) or raw manifests ([K8s](infra/k8s/kubernetes.md))

## Quick start

```bash
cp infra/.env.example infra/.env    # fill in required values
make compose-up                     # start the local dev stack
make smoke-test                     # verify endpoints
```

See [Deployment Testing](deployment-testing.md) for the full checklist, and [Secrets & Environment Variables](secrets-reference.md) for all configuration options.

## Developing

```bash
make generate       # regenerate OpenAPI spec + consumer clients
make spring-build   # compile and test Spring services
make preflight      # full pre-flight: generate, build, lint helm, validate terraform
```

See [OpenAPI Workflow](openapi-workflow.md) for the code-first spec pipeline.
