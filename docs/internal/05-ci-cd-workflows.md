# CI/CD Workflows (`.github/workflows/`)

## `ci.yml` — name: "CI"

Trigger: `push` (paths-ignore: `README.md`), `pull_request`, `workflow_dispatch`.

| Job | What it does |
| --- | --- |
| `build-and-test` | Matrix build across all 5 services: Gradle test (3 Spring modules), pytest (gen-ai), npm lint+build (web-client) |
| `openapi-contract` | Re-derives `api/openapi.yaml` from user-service + content-service (`OpenApiDocGenerationTest`), merges via `merge-openapi.py`, lints with Redocly, then **fails the build if the committed spec differs from the regenerated one** — real drift protection, not just a lint pass |
| `terraform-validate` | `terraform validate` over `infra/terraform/azure-vm` |
| `helm-lint` | `helm lint` over `infra/helm` |
| `contract-test` | **Currently a no-op**: checks `if [[ -x ./scripts/run-pact.sh ]]`; that script doesn't exist, so it just echoes "skipping" and passes. Not real contract/consumer-driven testing yet — see [07-gotchas.md](07-gotchas.md) |

## `upload_images.yml` — name: "build-and-package"

Trigger: `push`. Jobs: `prepare` → `build-backend` (multi-arch, all Spring +
gen-ai images) → `build-web-client` → `merge-web-client` (manifest merge for
multi-arch) → `finalize`. Pushes to GHCR.

## `deploy_kubernetes.yml` — name: "Deploy to Kubernetes"

Trigger: `workflow_run` on `build-and-package` completing for `main`, plus
`workflow_dispatch`. Single `deploy` job: `helm upgrade --install` against
`infra/helm`. `concurrency.cancel-in-progress: false` — deploys queue rather
than cancel each other.

## `deploy-azure.yml` — name: "Deploy to Azure"

Trigger: `push` (see file for path filters). Jobs: `verify-ci` (waits
on/checks CI status) → `build-and-push` (to ACR) → (deploy step via
`az vm run-command`, running `docker-compose.azure.yaml` on the VM).

## `destroy-azure.yml` — name: "Destroy Azure resources"

Manual only (`workflow_dispatch` with a `confirm` input) — tears down the
Azure VM/resources. **Destructive; never trigger without explicit user
instruction.**

## `publish_docs.yml` — name: "Publish Documentation"

Trigger: `push` to `main`/`dev`. Generates PlantUML diagrams (`docker run
plantuml/plantuml`), builds MkDocs (`docs/source/`), builds Redoc API docs
(`api/openapi.yaml` → `docs/source/api.html`), deploys to GitHub Pages.
**Only ever touches `docs/source/`** — this is why `docs/requirements/` and
`docs/internal/` (this folder) stay unpublished as long as they're kept
outside that tree.

## Re-verify

```
ls .github/workflows/
grep -n "^  [a-z-]*:$" .github/workflows/ci.yml
ls -la scripts/run-pact.sh 2>&1   # should still fail — if it now exists, contract-test is real
```
