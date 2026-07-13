# Infra & Deployment (`infra/`)

## Docker Compose (`infra/docker-compose*.yaml`)

Base file `infra/docker-compose.yaml` (project name `rolling-restarts`)
defines: `web-client`, `reverse-proxy` (nginx), `api-gateway`,
`user-service`, `content-service`, `gen-ai`, `mongodb`, `ollama` (optional,
`local-llm` profile), `grafana-lgtm-set-admin-password` (one-shot, resets
Grafana's admin password on every `up` — see `docs/internal/07-gotchas.md`),
`grafana-lgtm` (+ `mongodb-data`/`ollama-models`/`grafana-lgtm-data` volumes,
and `frontend`/`backend` networks).

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
not autoscaling], `serviceaccount.yaml`, `databases.yaml`, `monitoring.yaml`
[grafana-lgtm's Deployment/Service/PVC/Secrets/ConfigMaps, in its own
namespace], `monitoring-rbac.yaml` [ServiceAccount + Role/RoleBinding letting
Prometheus scrape pods], `_helpers.tpl`, `NOTES.txt`). Values split:
`values.yaml` (defaults) + `values-dev.yaml` / `values-prod.yaml` overrides,
plus `image-values.yaml` / `secrets-values.yaml` (with `.example`
counterparts — the real ones are gitignored/local or filled via CI secrets).

Ingress host configured: `rolling-restarts.stud.k8s.aet.cit.tum.de`
(`infra/helm/values.yaml`'s `host` key — re-check the exact line number
before citing it elsewhere, it moves as the file grows) — this is the
course's Rancher-managed cluster hostname. **Reachability confirmed live**
this branch, including the `/monitoring` path added for grafana-lgtm (see
`docs/source/monitoring.md`).

**RBAC is Rancher-managed, not git-managed** — for the `deployment` and
`monitoring-rolling-restarts` namespaces as they normally exist. Access
comes from Rancher's own project/namespace association, tied to a Keycloak
OIDC group (`devops26-team-the-rolling-restarts`) and a specific Rancher
project (`p-rjflh` on cluster `c-f49m7`) — not a plain Kubernetes
Role/RoleBinding this repo defines anywhere for those two namespaces
themselves (the Role/RoleBinding in `monitoring-rbac.yaml` only grants
grafana-lgtm's own ServiceAccount pod-scrape access, a separate concern).
If a namespace is ever deleted directly via `kubectl` (rather than through
Rancher) and recreated bare (`kubectl create namespace` or Helm's
`--create-namespace` alone), that project association does **not** come
back automatically — confirmed live.

**Recovery is now automated for the common case**: `infra/k8s/namespaces/
{deployment,monitoring}-namespace.yml` carry the same `field.cattle.io/
projectId` label/annotation a Rancher-created namespace gets, and
`.github/workflows/deploy_kubernetes.yml`/`deploy_monitoring.yml` apply them
via `kubectl create` whenever `kubectl get namespace` reports the namespace
missing entirely — restoring both RBAC and a real (non-zero) ResourceQuota
without any manual Rancher step, verified live against a throwaway test
namespace (see `docs/internal/07-gotchas.md`'s "manually-wiped namespace"
entry for the full mechanism and its one known gap: this check only
detects a **fully absent** namespace, not one that exists but is already
bare/mislabeled — that case still needs manual Rancher intervention via
`rancher.ase.cit.tum.de`, or a `kubectl label`/`kubectl annotate` patch
applying the same values by hand).

## Kubernetes — raw manifests (`infra/k8s/`)

Dual approach alongside Helm: `deployments/*.yml` and `services/*.yml` for
all workloads (api-gateway, content-service, gen-ai, mongodb, user-service,
web-client, grafana-lgtm), plus `ingress.yml`, `secrets.yml.example`,
`configmaps/` (grafana-lgtm's Prometheus/dashboards/alerting config, hand-
maintained — must stay byte-identical to the Helm/docker-compose copies,
see that file's own header comment), `rbac/` (grafana-lgtm's
ServiceAccount + Role/RoleBinding for pod scraping), and `namespaces/`
(the same disaster-recovery bootstrap manifests described above, for this
deployment path).

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
