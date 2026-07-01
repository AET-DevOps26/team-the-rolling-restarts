# CI/CD: Deploy to Azure

This document describes the `Deploy to Azure` GitHub Actions workflow
(`.github/workflows/deploy-azure.yml`), which builds the application's Docker
images, pushes them to **Azure Container Registry (ACR)**, and deploys them to
the **Azure VM** that runs the stack with Docker Compose.

It is intentionally separate from:

- `ci.yml` — build/test on every push & PR.
- `upload_images.yml` — publishes images to GHCR (used by the Helm/Kubernetes
  path on the TUM cluster).

## Pipeline overview

```
push to main / manual dispatch
        │
        ▼
┌──────────────────────┐     ┌────────────────────────────────────┐
│  build-and-push      │     │  deploy                             │
│  • gen OpenAPI code  │     │  • azure login                      │
│  • azure login       │ ──▶ │  • mint short-lived ACR token       │
│  • az acr login      │     │  • render .env from secrets         │
│  • build & push:     │     │  • az vm run-command (no SSH):      │
│    web-client        │     │    write compose+.env, docker login │
│    api-gateway       │     │    compose pull + up -d             │
│    user-service      │     │  • HTTP health check (in-VM curl)    │
│    content-service   │     │                                      │
│    gen-ai            │     │                                      │
└──────────────────────┘     └────────────────────────────────────┘
```

Images are tagged with both the short commit SHA (immutable, what gets
deployed) and `latest` (moving pointer).

The deploy job never opens an SSH connection to the VM. It drives the VM through
the **Azure control plane** with `az vm run-command`: the compose files and the
rendered `.env` are base64-embedded into a script that the VM's guest agent runs
as root. This means **port 22 can stay locked to your own IP** — GitHub-hosted
runner IPs never need to be allowed.

On the VM the stack is started from images instead of source by layering
`infra/docker-compose.azure.yaml` over `infra/docker-compose.yaml`. The override
removes the `build:` blocks and points the app services at
`${REGISTRY_BASE}/<service>:${IMAGE_TAG}`.

## Triggers

- `push` to `main`.
- `workflow_dispatch` (manual run from the Actions tab).

### CI gate

A `verify-ci` job runs first and blocks the build/deploy until the **CI** workflow for the same
commit has finished and passed (it polls the CI run for that SHA, up to ~15 min). If CI fails or
times out, nothing is built or deployed. Manual `workflow_dispatch` runs skip this wait so you can
force a deploy when needed.

## Prerequisites

1. **The Azure VM and the ACR provisioned** via the `infra/terraform/azure-vm`
   stack (the registry now lives in the same resource group as the VM — see
   `acr.tf`). Because `providers.tf` disables automatic provider registration,
   register the container-registry provider once per subscription:

   ```bash
   az provider register --namespace Microsoft.ContainerRegistry
   ```

   Then provision and read the registry details:

   ```bash
   cd infra/terraform/azure-vm
   terraform apply
   terraform output acr_login_server   # -> ACR_LOGIN_SERVER variable
   terraform output acr_name           # -> ACR_NAME variable
   ```

2. **Docker on the VM.** The Ansible `docker` role installs Docker + the Compose
   plugin; alternatively `curl -fsSL https://get.docker.com | sudo sh`. The
   deploy `DEPLOY_DIR` (e.g. `/opt/rolling-restarts`) must be writable by the
   process that runs the deploy (the run-command agent runs as root, so this is
   automatic).
3. **A service principal** with two scoped roles:
   - `AcrPush` on the registry — lets the build job push and lets the deploy job
     mint a pull token (`AcrPush` includes pull).
   - `Virtual Machine Contributor` on the resource group — lets the deploy job
     run `az vm run-command` against the VM.

   ```bash
   ACR_ID=$(az acr show --name <ACR_NAME> --query id -o tsv)
   RG_ID=$(az group show --name <RESOURCE_GROUP> --query id -o tsv)
   az ad sp create-for-rbac --name "rolling-restarts-deploy" \
     --role AcrPush --scopes "$ACR_ID"
   az role assignment create --assignee <appId> \
     --role "Virtual Machine Contributor" --scope "$RG_ID"
   ```

   Build the `AZURE_CREDENTIALS` secret from the output as JSON:
   `{"clientId","clientSecret","subscriptionId","tenantId"}`. (You can set
   `deploy_principal_id` in `terraform.tfvars` to the SP's **object ID** and
   Terraform will create **both** role assignments for you — see "Re-deploying
   after teardown" below.)

No SSH key is required for CI — the deploy job uses the Azure control plane, not
SSH.

## Re-deploying after teardown (stable configuration)

`terraform destroy` removes the ACR along with everything else, but the GitHub
configuration does **not** need to change when you re-create it:

- **The ACR name is deterministic.** It is derived from project/environment plus
  a hash of the subscription ID (no random suffix), so a fresh `terraform apply`
  always produces the **same** `acr_name` / `acr_login_server`. `ACR_NAME` and
  `ACR_LOGIN_SERVER` therefore stay constant. (Pin an exact name with the
  `acr_name` variable if you prefer.)
- **The resource group name is fixed** (`resource_group_name`), so
  `AZURE_RESOURCE_GROUP` and `DEPLOY_DIR` never change. The VM is looked up by
  resource group, so its random name suffix is irrelevant.
- **The service principal persists** (it is an Azure AD object, not a resource
  group resource), so `AZURE_CREDENTIALS` stays valid.
- **Role assignments are recreated automatically** when `deploy_principal_id` is
  set in `terraform.tfvars`: Terraform re-grants `AcrPush` on the new ACR and
  `Virtual Machine Contributor` on the resource group on every apply.

Net result: set the GitHub secrets/variables **once**. After a destroy, a single
`terraform apply` (with `deploy_principal_id` set) is enough to redeploy — no
GitHub changes required.

## Required GitHub secrets

Configure these under **Settings → Secrets and variables → Actions → Secrets**.
They are sensitive and are masked in logs.

| Secret | Description |
| --- | --- |
| `AZURE_CREDENTIALS` | Service-principal JSON (`clientId`/`clientSecret`/`subscriptionId`/`tenantId`) used by `azure/login`. |
| `LLM_API_KEY` | API key for the GenAI provider. |
| `MONGO_ROOT_USERNAME` | MongoDB root username. |
| `MONGO_ROOT_PASSWORD` | MongoDB root password. |
| `JWT_RSA_PUBLIC_KEY` | RSA public key (PEM) the user-service auth server publishes via JWKS. Stored as a single-line PEM (newlines stripped). If unset, user-service generates an ephemeral key that is lost on every restart, invalidating all issued tokens. |
| `JWT_RSA_PRIVATE_KEY` | RSA private key (PEM) the user-service auth server signs JWTs with. Single-line PEM; pair it with `JWT_RSA_PUBLIC_KEY`. |
| `SERVICE_CLIENT_SECRET` | Shared secret for the `client_credentials` token user-service uses to call content-service's subscribe/unsubscribe endpoints (scope `source.write`). Generate with `openssl rand -hex 32`. The deploy workflow fails fast if this is unset. |

The VM is targeted by name (looked up from `AZURE_RESOURCE_GROUP`) over the
Azure control plane, so no host/user/SSH-key secrets are needed.

## Required GitHub variables

Configure these under **Settings → Secrets and variables → Actions → Variables**.
They are non-sensitive configuration.

| Variable | Example | Description |
| --- | --- | --- |
| `ACR_NAME` | `myregistry` | ACR resource name (`terraform output acr_name`). Stable across destroy/recreate. |
| `ACR_LOGIN_SERVER` | `myregistry.azurecr.io` | ACR login server (`terraform output acr_login_server`). Stable across destroy/recreate. |
| `DEPLOY_DIR` | `/opt/rolling-restarts` | Directory on the VM the stack is deployed to. |
| `AZURE_RESOURCE_GROUP` | `rg-rolling-restarts-dev` | Resource group of the VM (deploy looks up the VM here; also used by the teardown workflow). |
| `NEXT_PUBLIC_API_BASE_URL` | `http://<vm-host>` | Public API URL baked into the web-client at build time. |
| `LLM_PROVIDER` | `openai` | GenAI provider. |
| `LLM_MODEL` | `gpt-4o-mini` | GenAI model. |
| `MONGO_DATABASE` | `mydatabase` | MongoDB database name. |

## Security notes

- **No inbound SSH from CI.** Deployment runs over the Azure control plane
  (`az vm run-command`), so the VM's NSG can keep SSH (port 22) restricted to
  your own IP — GitHub-hosted runner IP ranges never need to be allowed.
- **No long-lived registry password is stored.** The build job authenticates to
  ACR through the service principal; the deploy job mints a short-lived ACR
  access token (`az acr login --expose-token`) that the in-VM script pipes to
  `docker login` via stdin, then logs out at the end. The token is masked in
  logs.
- **Secrets never enter the repository.** The runtime `.env` is generated on the
  runner from GitHub secrets/variables with `umask 077`, base64-embedded into
  the run-command script (sent over TLS, not echoed), and is never committed.
- **Least-privilege service principal.** Scoped to `AcrPush` on the registry and
  `Virtual Machine Contributor` on the single resource group — nothing wider.
- **Environment protection:** the `deploy` job runs in the `production`
  GitHub Environment, so you can add required reviewers or branch restrictions
  in the repository settings.

## Running it

1. Add all secrets and variables listed above.
2. Push to `main`, or trigger **Actions → Deploy to Azure → Run workflow**.
3. Watch the run: `build-and-push` publishes images to ACR, then `deploy`
   updates the VM and runs the health check against the web client.

## Cost control & teardown (student subscriptions)

The VM is the main cost driver. To test cheaply and avoid leftover charges:

### Pause between test sessions

Stop (deallocate) the VM so you stop paying for compute. The OS disk and the
static public IP still incur a small charge, but the VM and its state are kept,
so you can resume quickly:

```bash
make azure-stop    # deallocate the VM (compute billing stops)
make azure-start   # power it back on when you want to test again
```

### Delete everything when done

The ACR lives in the **same resource group** as the VM, so a single destroy
removes the VM, registry, networking, and the resource group — nothing billable
is left behind. Pick whichever fits your situation:

```bash
# Clean teardown (keeps Terraform state in sync) - preferred
make terraform-destroy

# State-independent nuke (deletes the whole resource group directly).
# Use AZURE_RG=<name> if you customized resource_group_name.
make azure-nuke
```

You can also trigger teardown from the GitHub UI:
**Actions → Destroy Azure resources → Run workflow**, then type `destroy` to
confirm. This runs `az group delete` and **bypasses Terraform state**, so after
using it, clear local state (delete the local `terraform.tfstate*` files or run
`terraform state rm`) before the next `terraform apply`. The
`AZURE_CREDENTIALS` service principal must have permission to delete the
resource group for this workflow to succeed.

> Tip: a fresh ACR build/push + VM deploy + immediate `make terraform-destroy`
> keeps the spend to a few minutes of B-series VM time plus a tiny amount of
> Basic ACR storage.

## Verifying / troubleshooting

Inspect the stack without SSH using the same control-plane mechanism:

```bash
VM=$(az vm list -g <RESOURCE_GROUP> --query "[0].name" -o tsv)
az vm run-command invoke -g <RESOURCE_GROUP> -n "$VM" \
  --command-id RunShellScript \
  --scripts 'cd /opt/rolling-restarts && docker compose --env-file .env -f docker-compose.yaml -f docker-compose.azure.yaml ps'
```

Or, if your IP is allowed by the NSG, SSH in directly:

```bash
ssh <admin_user>@<vm_public_ip>
cd /opt/rolling-restarts
docker compose --env-file .env -f docker-compose.yaml -f docker-compose.azure.yaml ps
cat /var/log/rr-deploy.log         # pull/up output from the last deploy
```

- **`AuthorizationFailed` on run-command:** the service principal is missing
  `Virtual Machine Contributor` on the resource group.
- **Image pull denied:** confirm the SP has `AcrPush` on the registry and the
  `ACR_LOGIN_SERVER` / `ACR_NAME` values match.
- **Deploy reports `HEALTH_FAILED`:** check `/var/log/rr-deploy.log` and the
  `web-client`/`api-gateway`/`user-service`/`content-service` logs; the API
  waits on healthy MongoDB/gen-ai containers before it starts.
- **Note:** the in-browser app calls `NEXT_PUBLIC_API_BASE_URL`. The default NSG
  opens port 80 (`application_ports = [80]` in `terraform.tfvars.example`).
