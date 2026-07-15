# CI/CD: Deploy to Kubernetes

This document describes the `Deploy to Kubernetes` GitHub Actions workflow
(`.github/workflows/deploy_kubernetes.yml`), which deploys the application to
a **Kubernetes cluster** using the Helm chart in `infra/helm/`.

It is intentionally separate from:

- `ci.yml` — build/test on every push & PR.
- `upload_images.yml` — builds and pushes images to GHCR, then uploads an
  `image-values` artifact consumed by this workflow.
- `deploy-azure.yml` — deploys to the Azure VM with Docker Compose.
- `deploy_monitoring.yml` — a lighter sibling that redeploys only `grafana-lgtm` (its own
  namespace, `monitoring-rolling-restarts`) when monitoring-related files change
  (`infra/grafana/**`, the monitoring Helm templates, etc.). It doesn't wait on
  `build-and-package`/CI at all — `grafana-lgtm` runs an upstream public image this repo never
  builds — and uses `helm upgrade --reuse-values` so it never needs the app secrets or
  image-values this workflow does. It shares this workflow's `deploy-k8s` concurrency group so
  the two never run `helm upgrade` against the same release at the same time. Requires this
  workflow to have deployed the release at least once already.

## Pipeline overview

```
push (any branch)                  workflow_run (on success)
       │                                    │
       ▼                                    ▼
┌──────────────────────┐     ┌────────────────────────────────────┐
│  build-and-package   │     │  deploy                             │
│  (upload_images.yml) │     │  (deploy_kubernetes.yml)            │
│  • gen OpenAPI code  │     │  • download image-values artifact   │
│  • docker login GHCR │ ──▶ │  • setup Helm + kubeconfig          │
│  • build & push:     │     │  • generate secrets-values.yaml     │
│    web-client        │     │  • helm upgrade --install            │
│    api-gateway       │     │  • kubectl rollout status (verify)   │
│    user-service      │     │                                      │
│    content-service   │     │                                      │
│    gen-ai            │     │                                      │
│  • upload artifact   │     │                                      │
│    (image-values)    │     │                                      │
└──────────────────────┘     └────────────────────────────────────┘
```

Images are tagged with the short commit SHA (immutable, what gets deployed) and
a channel tag (`main`, `dev`, or `wip`).

## Triggers

- **`workflow_run`** — automatically runs after the `build-and-package` workflow
  completes successfully. The deploy workflow must exist on the default branch
  for this trigger to fire.
- **`workflow_dispatch`** — manual run from the Actions tab. Uses
  `image-values.example.yaml` as a fallback since no artifact is available.

### CI gate

`build-and-package` runs on every push regardless of CI, so the deploy job's first step requires
that the **CI** workflow for the same commit has finished and passed (it polls the CI run for that
SHA, up to ~15 min) before touching the cluster. If CI failed or never ran, the deploy aborts.
Manual `workflow_dispatch` runs skip this check.

## Environment selection

The workflow is triggered only when `build-and-package` runs on the `main` branch (`branches: [main]` filter on the `workflow_run` trigger), so it always deploys to production:

| GitHub Environment | Helm profile | Values files |
| --- | --- | --- |
| `production` | `prod` | `values.yaml` + `values-prod.yaml` + `secrets-values.yaml` + `image-values.yaml` |

The `values-prod.yaml` overlay increases replica counts and switches to the
production TLS cluster issuer.

## Target cluster

The workflow targets a Rancher-managed Kubernetes cluster at TUM
(`rancher.ase.cit.tum.de`). The kubeconfig is stored as a base64-encoded
GitHub secret and includes the target namespace (`deployment`) in its context.

## Required GitHub secrets

Configure these under **Settings → Secrets and variables → Actions → Secrets**.
They are sensitive and are masked in logs.

| Secret | Description |
| --- | --- |
| `KUBECONFIG_BASE64` | Base64-encoded kubeconfig for the target cluster. Must include the target namespace in the context. Generate with `base64 -w0 < kubeconfig`. |
| `MONGO_ROOT_USERNAME` | MongoDB root username. |
| `MONGO_ROOT_PASSWORD` | MongoDB root password. |
| `JWT_RSA_PUBLIC_KEY` | RSA public key (PEM) for JWT signing. Shared across all user-service replicas. |
| `JWT_RSA_PRIVATE_KEY` | RSA private key (PEM) for JWT signing. Must match the public key. |
| `SERVICE_CLIENT_SECRET` | Shared secret for the `client_credentials` token user-service uses to call content-service's subscribe/unsubscribe endpoints (scope `source.write`). Generate with `openssl rand -hex 32`. |
| `LLM_API_KEY` | Logos API key (`lg-...`, from tutor) for gen-ai's cloud LLM calls. Optional — falls back to an empty string if unset, so gen-ai starts but its LLM-backed endpoints fail until this is set. Only reachable from the TUM network / eduVPN (true for this Kubernetes cluster, not the Azure VM target). |
| `GRAFANA_ADMIN_PASSWORD` | Admin login for grafana-lgtm, reachable at `/monitoring` behind the shared ingress. Generate with `openssl rand -hex 16`. The deploy workflow fails fast if this is unset. |
| `GRAFANA_SMTP_USER` | Sending email address for alert notifications (Gmail: the account itself). The deploy workflow fails fast if this, `GRAFANA_SMTP_PASSWORD`, or `GRAFANA_ALERT_EMAILS` is unset. |
| `GRAFANA_SMTP_PASSWORD` | SMTP auth password. Gmail: an App Password, NOT the account password — requires 2FA on that account, generate at myaccount.google.com/apppasswords. |
| `GRAFANA_ALERT_EMAILS` | Comma-separated recipient list for the `email-alerts` contact point. |

### Generating the JWT key pair

Generate a stable RSA key pair and store both as secrets:

```bash
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out private.pem
openssl pkey -in private.pem -pubout -out public.pem
gh secret set JWT_RSA_PUBLIC_KEY < public.pem
gh secret set JWT_RSA_PRIVATE_KEY < private.pem
rm private.pem public.pem
```

Use the same key pair across deploys to avoid invalidating existing user
sessions (JWTs signed with the old key would fail validation).

### Setting up the kubeconfig

Obtain the kubeconfig from the Rancher UI or your cluster admin. Ensure the
target namespace is set in the context:

```bash
kubectl config set-context --current --namespace=deployment \
  --kubeconfig=/path/to/kubeconfig
gh secret set KUBECONFIG_BASE64 < <(base64 -w0 < /path/to/kubeconfig)
```

## Required GitHub variables

No variables are required. The workflow always targets the `production` environment and the `prod` Helm profile.

## How secrets are injected

The workflow generates `infra/helm/secrets-values.yaml` at runtime using a
Python script that reads from environment variables and produces valid YAML.
This avoids shell heredoc issues with multiline PEM keys. The file is never
committed — it exists only on the runner for the duration of the deploy.

The generated file overrides:

- `mongodb.rootUsername` / `mongodb.rootPassword` — used by the MongoDB
  StatefulSet init and the connection URI secrets.
- `userService.jwtKeys.publicKey` / `userService.jwtKeys.privateKey` — mounted
  as a Kubernetes Secret and consumed by user-service for JWT signing.
- `userService.serviceClientSecret` — mounted as the `service-credentials` Kubernetes Secret and used by user-service to obtain a `client_credentials` token for calling content-service's subscribe/unsubscribe endpoints.

## Resource quotas

`grafana-lgtm` runs in its own dedicated namespace (`monitoring.namespace` in
`infra/helm/values.yaml`, currently `monitoring-rolling-restarts`) with its own `ResourceQuota`,
separate from the app workloads' namespace — see `docs/source/monitoring.md`'s "Monitoring runs
in its own namespace" section. The app namespace's quota is **3500m CPU / 5244Mi memory**, sized
with headroom for **api-gateway, user-service, content-service, gen-ai, and web-client to each run
3 replicas simultaneously** (in anticipation of a future HPA), alongside the `mongodb` singleton
(`replicas: 1`, not horizontally scaled):

| Workload | Replicas | CPU limit | Memory limit | ×N CPU | ×N Memory |
| --- | --- | --- | --- | --- | --- |
| web-client | 3 | 100m | 200Mi | 300m | 600Mi |
| api-gateway | 3 | 220m | 330Mi | 660m | 990Mi |
| user-service | 3 | 220m | 460Mi | 660m | 1380Mi |
| content-service | 3 | 220m | 400Mi | 660m | 1200Mi |
| gen-ai | 3 | 100m | 130Mi | 300m | 390Mi |
| mongodb | 1 | 250m | 512Mi | 250m | 512Mi |
| **App total** | | | | **2830m / 3500m** | **5072Mi / 5244Mi** |

The monitoring namespace's quota is a separate **500m CPU / 900Mi memory**:

| Workload | Replicas | CPU limit | Memory limit |
| --- | --- | --- | --- |
| grafana-lgtm | 1 | 400m | 850Mi |

See `docs/source/monitoring.md`'s "Kubernetes resource budget" section for the full rationale.
If your namespaces have smaller `ResourceQuota`s, scale these down and reduce `global.replicas`
(`values-prod.yaml` runs at 2 replicas) accordingly.

## Security notes

- **Kubeconfig is short-lived.** The base64-decoded kubeconfig exists only in
  `~/.kube/config` on the runner for the duration of the job. Runners are
  ephemeral.
- **Secrets never enter the repository.** `secrets-values.yaml` is generated on
  the runner from GitHub secrets with `umask 077` and is never committed.
- **JWT keys are stable.** Unlike generating keys on every deploy, storing them
  as secrets preserves user sessions across deployments.
- **GHCR image pull.** The cluster must be able to pull from `ghcr.io`. If the
  repository is private, configure `imagePullSecrets` in `values.yaml`.
- **Environment protection.** The deploy job uses GitHub Environments, so you
  can add required reviewers or branch restrictions under repository settings.

## Running it

1. Add all secrets listed above.
2. Push to any branch (triggers `build-and-package`, then `deploy`), or
   trigger **Actions → Deploy to Kubernetes → Run workflow**.
3. Watch the run: `build-and-package` publishes images to GHCR and uploads the
   `image-values` artifact, then `deploy` installs/upgrades the Helm release.

## Verifying / troubleshooting

```bash
# Check pod status
kubectl get pods -n deployment

# Check deployment rollout
kubectl rollout status deployment/api-gateway -n deployment

# View logs for a service
kubectl logs -n deployment deployment/api-gateway

# Describe a failing pod
kubectl describe pod -n deployment <pod-name>

# Check Helm release status
helm list -n deployment
helm history newsgenai -n deployment
```

- **`exceeded quota`:** the namespace `ResourceQuota` doesn't allow the
  requested CPU/memory. Check with `kubectl get resourcequota -n deployment`
  and increase the limits.
- **`pending-install` / `another operation in progress`:** a previous Helm
  release is stuck. Clean up with `helm uninstall newsgenai -n deployment` or,
  if that fails, `kubectl delete secret -n deployment -l name=newsgenai,owner=helm`.
  Unlike `make helm-destroy` (which only tears down the app workloads), a raw `helm uninstall`
  here removes `grafana-lgtm`'s monitoring stack too — it's a genuine full recovery, not the
  everyday teardown path.
- **`context deadline exceeded`:** pods didn't become ready within the 5-minute
  timeout. Check pod events and logs for image pull errors or crash loops.
- **Image pull errors:** ensure the cluster can reach `ghcr.io` and, for
  private repos, that `imagePullSecrets` is configured.
- **`workflow_run` not triggering:** this trigger only works when the workflow
  file exists on the repository's default branch. Merge the workflow to
  `main` first.
