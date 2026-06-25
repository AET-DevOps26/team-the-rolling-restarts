# CI/CD: Deploy to Kubernetes

This document describes the `Deploy to Kubernetes` GitHub Actions workflow
(`.github/workflows/deploy_kubernetes.yml`), which deploys the application to
a **Kubernetes cluster** using the Helm chart in `infra/helm/`.

It is intentionally separate from:

- `ci.yml` — build/test on every push & PR.
- `upload_images.yml` — builds and pushes images to GHCR, then uploads an
  `image-values` artifact consumed by this workflow.
- `deploy-azure.yml` — deploys to the Azure VM with Docker Compose.

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

## Environment selection

The workflow dynamically selects the GitHub Environment and Helm values based
on the branch that triggered the build:

| Branch | GitHub Environment | Helm profile | Values files |
| --- | --- | --- | --- |
| `main` | `production` | `prod` | `values.yaml` + `values-prod.yaml` + `secrets-values.yaml` + `image-values.yaml` |
| any other | `dev` | `dev` | `values.yaml` + `secrets-values.yaml` + `image-values.yaml` |

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

No variables are required. The workflow derives the environment and Helm
profile from the branch automatically.

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

## Resource quotas

The Helm chart's resource limits are sized to fit within a namespace quota of
**2 CPU / 3 GB memory** (1 replica per workload):

| Workload | CPU limit | Memory limit |
| --- | --- | --- |
| web-client | 250m | 384Mi |
| api-gateway | 350m | 448Mi |
| user-service | 350m | 448Mi |
| content-service | 350m | 448Mi |
| gen-ai | 250m | 384Mi |
| mongodb | 150m | 384Mi |
| **Total** | **1700m** | **2496Mi** |

If your namespace has a `ResourceQuota`, ensure it allows at least these
totals. Running with `values-prod.yaml` (2 replicas) requires proportionally
more quota.

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
- **`context deadline exceeded`:** pods didn't become ready within the 5-minute
  timeout. Check pod events and logs for image pull errors or crash loops.
- **Image pull errors:** ensure the cluster can reach `ghcr.io` and, for
  private repos, that `imagePullSecrets` is configured.
- **`workflow_run` not triggering:** this trigger only works when the workflow
  file exists on the repository's default branch. Merge the workflow to
  `main` first.
