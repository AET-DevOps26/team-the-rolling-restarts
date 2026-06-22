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
┌──────────────────────┐     ┌───────────────────────────────┐
│  build-and-push      │     │  deploy                        │
│  • gen OpenAPI code  │     │  • azure login                 │
│  • azure login       │ ──▶ │  • mint short-lived ACR token  │
│  • az acr login      │     │  • render .env from secrets    │
│  • build & push:     │     │  • scp compose + .env to VM    │
│    web-client        │     │  • ssh: docker login + compose │
│    spring-api        │     │    pull + up -d                │
│    gen-ai            │     │  • HTTP health check           │
└──────────────────────┘     └───────────────────────────────┘
```

Images are tagged with both the short commit SHA (immutable, what gets
deployed) and `latest` (moving pointer).

On the VM the stack is started from images instead of source by layering
`infra/docker-compose.azure.yaml` over `infra/docker-compose.yaml`. The override
removes the `build:` blocks and points the app services at
`${REGISTRY_BASE}/<service>:${IMAGE_TAG}`.

## Triggers

- `push` to `main`.
- `workflow_dispatch` (manual run from the Actions tab).

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

2. **The VM configured** via `infra/ansible`, with Docker and the Docker Compose
   plugin installed (the Ansible `docker` role handles this).
3. **A service principal** with `AcrPush` (build job) + pull (token used on the
   VM) on the registry:

   ```bash
   az ad sp create-for-rbac \
     --name "rolling-restarts-deploy" \
     --role AcrPush \
     --scopes $(az acr show --name <ACR_NAME> --query id -o tsv) \
     --sdk-auth
   ```

   The JSON printed by `--sdk-auth` is the value of the `AZURE_CREDENTIALS`
   secret. Alternatively, set `deploy_principal_id` in `terraform.tfvars` to the
   service principal's object ID and Terraform will create the role assignment
   for you.
4. **An SSH key pair** whose public key is an authorized key for the VM admin
   user. The private key becomes `AZURE_VM_SSH_PRIVATE_KEY`.

## Required GitHub secrets

Configure these under **Settings → Secrets and variables → Actions → Secrets**.
They are sensitive and are masked in logs.

| Secret | Description |
| --- | --- |
| `AZURE_CREDENTIALS` | Service-principal JSON (`az ad sp create-for-rbac --sdk-auth`) used by `azure/login`. |
| `AZURE_VM_HOST` | Public IP or DNS name of the Azure VM (`terraform output vm_public_ip`). |
| `AZURE_VM_USER` | VM admin username (`terraform output admin_username`). |
| `AZURE_VM_SSH_PRIVATE_KEY` | Private SSH key (PEM) authorized on the VM. |
| `LLM_API_KEY` | API key for the GenAI provider. |
| `MONGO_ROOT_USERNAME` | MongoDB root username. |
| `MONGO_ROOT_PASSWORD` | MongoDB root password. |
| `POSTGRES_USER` | PostgreSQL username. |
| `POSTGRES_PASSWORD` | PostgreSQL password. |

## Required GitHub variables

Configure these under **Settings → Secrets and variables → Actions → Variables**.
They are non-sensitive configuration.

| Variable | Example | Description |
| --- | --- | --- |
| `ACR_NAME` | `myregistry` | ACR resource name (`terraform output acr_name`). |
| `ACR_LOGIN_SERVER` | `myregistry.azurecr.io` | ACR login server (`terraform output acr_login_server`). |
| `DEPLOY_DIR` | `/opt/rolling-restarts` | Directory on the VM the stack is deployed to. |
| `AZURE_RESOURCE_GROUP` | `rg-rolling-restarts-dev` | Resource group deleted by the `Destroy Azure resources` workflow. |
| `NEXT_PUBLIC_API_BASE_URL` | `http://<vm-host>:8080` | Public API URL baked into the web-client at build time. |
| `LLM_PROVIDER` | `openai` | GenAI provider. |
| `LLM_MODEL` | `gpt-4o-mini` | GenAI model. |
| `MONGO_DATABASE` | `mydatabase` | MongoDB database name. |
| `POSTGRES_DB` | `mydatabase` | PostgreSQL database name. |

## Security notes

- **No long-lived registry password is stored.** The build job authenticates to
  ACR through the service principal; the deploy job mints a short-lived ACR
  access token (`az acr login --expose-token`) and pipes it to `docker login`
  on the VM via stdin. The token is masked in logs and the VM logs out of the
  registry at the end of the run.
- **Secrets never enter the repository.** The runtime `.env` is generated on the
  runner from GitHub secrets/variables with `umask 077`, copied to the VM, and
  is never committed.
- **Host key verification** uses `ssh-keyscan` on each run (trust-on-first-use).
  For stronger guarantees, pin the VM host key in a known-hosts secret instead.
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

## Verifying / troubleshooting on the VM

```bash
ssh <AZURE_VM_USER>@<AZURE_VM_HOST>
cd /opt/rolling-restarts            # or your DEPLOY_DIR
docker compose --env-file .env \
  -f docker-compose.yaml -f docker-compose.azure.yaml ps
docker compose --env-file .env \
  -f docker-compose.yaml -f docker-compose.azure.yaml logs -f web-client
```

- **Image pull denied:** confirm the service principal has `AcrPull` and the
  `ACR_LOGIN_SERVER` / `ACR_NAME` values match the registry.
- **SSH failures:** confirm `AZURE_VM_SSH_PRIVATE_KEY` matches an authorized key
  and that the VM's network security group allows SSH from GitHub-hosted runners
  (or use a self-hosted runner inside the VNet).
- **Health check timeout:** inspect `web-client` and `spring-api` logs; the API
  depends on healthy MongoDB/PostgreSQL/gen-ai containers before it starts.
