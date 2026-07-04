# 05 — Environment & Deployment

## Local containerisation

- **All** components must be fully containerised: client, server-side services, GenAI
  service, and database.
- **Each component must have its own Dockerfile.**
- The local setup must run the system **end-to-end** via a `docker-compose.yml`.

## Simple setup (hard constraint)

- The system must be runnable in **three or fewer commands** (e.g. `docker compose up`).
- Must provide **sane defaults** — no long manual configuration or complex environment
  preparation.
- A new user must be able to start the system **without reverse-engineering** the
  project.

## Kubernetes

- The same system must also be deployable to **Kubernetes**.
- Deployment may use **Helm charts** or **raw Kubernetes manifests**.
- Must support:
  - the **course infrastructure via Rancher**, and
  - **one cloud environment** — in this project, **Azure**.

## Configuration (no hardcoding)

- Configuration must be **externalised** using environment variables, Secrets, and
  similar mechanisms.
- **Not acceptable:** hardcoded credentials, hardcoded environment-dependent values, or
  manual configuration in the code.

## Summary table

| Aspect | Requirement |
|--------|-------------|
| Containerisation | Every component (server, client, GenAI, DB) has its own Dockerfile |
| Local orchestration | `docker-compose.yml` runs the system end-to-end locally |
| Setup | Runnable in ≤ 3 commands; no complex manual ENV setup |
| Kubernetes | Deployable using Helm or raw manifests |
| Environments | Local infrastructure (Rancher) **and** a cloud option (Azure) |

> CI/CD deployment automation is covered in [06-ci-cd.md](06-ci-cd.md).
