# Infra & Deployment (`infra/`)

## Docker Compose (`infra/docker-compose*.yaml`)

Base file `infra/docker-compose.yaml` (project name `rolling-restarts`)
defines: `web-client`, `reverse-proxy` (nginx), `api-gateway`,
`user-service`, `content-service`, `gen-ai`, `mongodb`, `grafana-lgtm`
(+ `mongodb-data` volume, and `frontend`/`backend` networks).

Overlay files, combined via `-f base -f overlay`:

| File | Purpose |
| --- | --- |
| `docker-compose.dev.yaml` | Local hot-reload dev overrides (build context, dev Dockerfile target) |
| `docker-compose.prod.yaml` | Azure VM production overlay — clamps every host-published port **except the reverse-proxy** (`APP_PORT`, default 80) to `127.0.0.1`, so only the proxy is reachable on the VM's public IP |
| `docker-compose.azure.yaml` | Used by `deploy-azure` workflow: runs from **pre-built images pulled from ACR** instead of building from source |
| `docker-compose.test.yaml` | CI/test overlay — resets some services' ports/profiles (e.g. `web-client` profile reset to `manual`, ports reset to `[]`) |

Local quick start (documented in `docs/source/index.md`, **not yet in root
`README.md`**): `cp infra/.env.example infra/.env && make compose-up`.

## Kubernetes — Helm (`infra/helm/`)

Real chart: `Chart.yaml` + `templates/` (`deployment.yaml`, `service.yaml`,
`ingress.yaml`, `secrets.yaml`, `pdb.yaml` [PodDisruptionBudget — availability,
not autoscaling], `serviceaccount.yaml`, `databases.yaml`, `_helpers.tpl`,
`NOTES.txt`). Values split: `values.yaml` (defaults) + `values-dev.yaml` /
`values-prod.yaml` overrides, plus `image-values.yaml` /
`secrets-values.yaml` (with `.example` counterparts — the real ones are
presumably gitignored/local or filled via CI secrets).

Ingress host configured: `rolling-restarts.stud.k8s.aet.cit.tum.de`
(`infra/helm/values.yaml:225`) — this is the course's Rancher-managed
cluster hostname. **Reachability not verified from this sandbox (no network
egress)** — check manually.

**RBAC is Rancher-managed, not git-managed.** Access to the `deployment` and
`monitoring-rolling-restarts` namespaces comes from Rancher's own
project/namespace association, tied to a Keycloak OIDC group
(`devops26-team-the-rolling-restarts`) — not a plain Kubernetes
Role/RoleBinding this repo defines anywhere. If a namespace is ever deleted
directly via `kubectl` (rather than through Rancher), that project
association breaks and does not come back just from recreating the
namespace at the raw Kubernetes API level (`kubectl create namespace` or
Helm's `--create-namespace`) — confirmed live, see
`docs/internal/06-observability.md`'s disaster-recovery entry. Recovering
from that requires going through Rancher (`rancher.ase.cit.tum.de`)
directly; nothing in this repo's CI/CD can do it.

## Kubernetes — raw manifests (`infra/k8s/`)

Dual approach alongside Helm: `deployments/*.yml` and `services/*.yml` for
all 6 workloads (api-gateway, content-service, genai, mongodb, user-service,
web-client), plus `ingress.yml` and `secrets.yml.example`.

## Cloud provisioning

- `infra/terraform/azure-vm/` — Azure VM provisioning
- `infra/ansible/` — VM configuration/deployment roles (`common`, `docker`, `app` per `CLAUDE.md`)
- Manual runbook: `docs/source/azure-vm-deployment.md`
- CI/CD-driven Azure path: `.github/workflows/deploy-azure.yml` (see
  [05-ci-cd-workflows.md](05-ci-cd-workflows.md))

## Re-verify

```sh
grep -n "^  [a-zA-Z0-9_-]*:$" infra/docker-compose.yaml     # service list
find infra/helm -maxdepth 2 -type f
find infra/k8s -type f
grep -n "host:" infra/helm/values.yaml
```
