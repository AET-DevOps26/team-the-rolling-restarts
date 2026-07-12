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

Trigger: `push`, `workflow_dispatch`. Jobs: `prepare` (computes `owner`/
`project`/`sha`/`channel` — `channel` is `main`/`dev` for those branches,
`wip` otherwise; no longer emits an `api_base_url` output, see below) →
`build-backend` (multi-arch, all 3 Spring + gen-ai images) →
`build-web-client` (native per-arch, since Next.js's SWC crashes under QEMU
arm64 emulation) → `merge-web-client` (manifest merge for multi-arch) →
`finalize` (emits the `image-values` artifact other workflows consume).
Pushes to GHCR, tagged `:<sha>` and `:<channel>`.

**Web-client images are portable across every deployment target now** — no
build-arg bakes in a deployment-specific API URL anymore (`web-client`'s
`API_BASE_URL` is read at container runtime instead; see
`docs/internal/06-observability.md` for the incident this fixes). Previously
this workflow baked a hardcoded, stale Kubernetes hostname into every
web-client image via `NEXT_PUBLIC_API_BASE_URL`, silently breaking every
server-side API call (signup, login, …) on whichever target consumed that
image — the `api_base_url` output/build-arg was removed entirely as part of
that fix, not just left unused.

## `deploy_kubernetes.yml` — name: "Deploy to Kubernetes"

Trigger: `workflow_run` on `build-and-package` completing for `main`
(automatic, on every merge), plus `workflow_dispatch` (manual). Single
`deploy` job, `--wait --timeout 10m`, `--create-namespace`.
`concurrency.cancel-in-progress: false` — deploys queue rather than cancel
each other.

Both trigger types now share one code path via a "Resolve build-and-package
run to deploy" step:

- `workflow_run` reads the SHA/run-id the event already carries (unchanged
  behavior).
- `workflow_dispatch` looks up the most recent **successful**
  `build-and-package` run on whichever branch/ref the dispatch itself runs
  against (`github.ref_name`) and downloads *that* run's real
  `image-values` artifact. It previously fell back to
  `image-values.example.yaml` (placeholder image references) with no CI
  gate at all — now it gets real images and the same CI-pass gate
  `workflow_run` already had.

`--create-namespace` lets a manual dispatch recover from the app's own
namespace (`deployment`) being wiped entirely — it only covers Helm's own
release-target namespace, never `monitoring-rolling-restarts` (a separate
namespace referenced via an explicit `metadata.namespace` elsewhere in the
chart, which is why the `Ensure monitoring namespace exists` step still
exists separately). **This only recreates the bare namespace + whatever
`ResourceQuota` a cluster-level admission policy auto-attaches — it does
NOT restore RBAC access**, which on this Rancher-managed cluster is tied to
a Keycloak project association that breaks when a namespace is deleted
directly via `kubectl` and does not come back this way. See
`docs/internal/06-observability.md`'s disaster-recovery entry and
`docs/internal/04-infra-and-deploy.md` for the full writeup — recovering
from that needs going through Rancher directly.

Secrets wired into `infra/helm/secrets-values.yaml` at deploy time: Mongo
root credentials, JWT RSA key pair, `SERVICE_CLIENT_SECRET`, and
`LLM_API_KEY` (gen-ai's Logos API key — previously missing from this list
entirely, so gen-ai always deployed with an empty key and every LLM call
failed with `Missing credentials`, fixed by adding it here).

## `deploy_monitoring.yml` — name: "Deploy Monitoring"

Trigger: `push` to `main`, path-filtered to monitoring-only files
(`infra/grafana/**`, `infra/helm/files/grafana/**`, the monitoring Helm
templates, the raw-manifest equivalents), plus `workflow_dispatch`. Lets a
dashboard/provisioning-only change redeploy without waiting on the full
app build/CI pipeline — `grafana-lgtm` runs an upstream public image
(`grafana/otel-lgtm`), nothing here gets built or pushed by this repo.
Uses `helm upgrade --reuse-values` (not `--install`, requires the release
to already exist) so it never needs Mongo/JWT/service-client secrets or
image tags — only the monitoring-related values change. Shares
`deploy_kubernetes.yml`'s `deploy-k8s` concurrency group so the two never
run `helm upgrade` against the same release simultaneously.

## `deploy-azure.yml` — name: "Deploy to Azure"

Trigger: `push` (see file for path filters), `workflow_dispatch`. Jobs:
`build-and-push` (builds web-client + all 3 Spring + gen-ai images, pushes
to ACR) → `deploy` (waits on CI for the same commit, then deploys via
`az vm run-command`, running `docker-compose.azure.yaml` on the VM).

The "Build remote deployment script" step now also tar+base64-embeds
`infra/grafana/` into the remote script (wipe-then-extract on the VM side)
— it never synced this before, so `grafana-lgtm`'s bind-mounted config
(`prometheus.yaml`, dashboards, provisioning) had nothing to mount on a
fresh VM, and Docker's fallback (auto-creating the missing source as an
empty directory) broke the container outright. Same class of fix as the
Ansible `app` role's equivalent (`docs/internal/06-observability.md`).

**Known limitation, not a workflow bug**: this deployment target's gen-ai
LLM calls don't currently work. gen-ai's `logos` provider
(`https://logos.aet.cit.tum.de/v1`) is TUM-network-only and unreachable
from Azure's public cloud, and no Ollama instance is provisioned on this
target either (the `ollama` compose service exists but is gated behind a
profile nothing in this path activates). Everything else on this target
(signup/login, dashboards, mongodb, grafana-lgtm) works.

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

```sh
ls .github/workflows/
grep -n "^  [a-z-]*:$" .github/workflows/ci.yml
ls -la scripts/run-pact.sh 2>&1   # should still fail — if it now exists, contract-test is real
```
