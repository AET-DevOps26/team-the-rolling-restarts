# Azure VM Deployment Runbook (Manual / Ansible)

This runbook documents **manual** provisioning and deployment via Terraform + Ansible.
For the automated CI/CD pipeline (GitHub Actions → ACR → `az vm run-command`), see [Azure CD Pipeline](cicd-azure-deploy.md).

## Validated student-account profile

The following values were validated on an Azure for Students subscription:

- `location`: `germanywestcentral`
- `vm_size`: `Standard_B2ps_v2`
- `vm_image_sku`: `server-arm64`
- `resource_provider_registrations` in provider: `none`

## 1) Provision Azure infrastructure (Terraform)

```bash
cd infra/terraform/azure-vm
cp terraform.tfvars.example terraform.tfvars
# edit terraform.tfvars
terraform init
terraform plan
terraform apply
```

Required Terraform values for the profile above:

```hcl
location     = "germanywestcentral"
vm_size      = "Standard_B2ps_v2"
vm_image_sku = "server-arm64"
```

Least-privilege note:

- Keep `application_ports = [80]` — the nginx reverse proxy is the single public entry point (it serves the web client at `/` and proxies `/api` to the gateway). On the VM, `APP_PORT` defaults to `80` (set in `docker-compose.prod.yaml` and `all.yml.example`).
- Backend service ports (8081/8082/8000), MongoDB (27017), and Grafana (3001) are bound to the VM's loopback by `docker-compose.prod.yaml`, so they are never exposed publicly. Reach them via an SSH tunnel rather than opening NSG ports.

Capture connection details:

```bash
terraform output vm_public_ip
terraform output admin_username
terraform output ssh_command
```

## Image architecture

The validated VM profile (`Standard_B2ps_v2`) is **arm64**. Container images must be built for the matching architecture or the containers will fail to start with `exec format error`.

> **arm64 images:** `make push-images` does not support `PLATFORM=linux/arm64` — Next.js's SWC compiler crashes with SIGILL under QEMU arm64 emulation, so local cross-builds are blocked. Use the CI workflow (`upload_images.yml`) instead: it builds `web-client` natively on an `ubuntu-24.04-arm` runner and produces a multi-arch manifest automatically on every push to `main` or `dev`.

To deploy a specific commit to the VM, push the branch to trigger CI, then deploy the resulting image tag:

```bash
# After CI produces the multi-arch image:
ansible-playbook infra/ansible/playbooks/deploy.yml -e "image_tag=<sha>"
```

See [Cross-architecture builds](deployment-testing.md#cross-architecture-builds) for background on the per-arch CI strategy.

## 2) Configure and deploy on VM (Ansible)

```bash
./infra/scripts/generate-ansible-inventory.sh
cd infra/ansible
cp group_vars/all.yml.example group_vars/all.yml
# edit inventory + vars (repo URL, branch, secrets)
ansible-playbook playbooks/deploy.yml
```

Trust the new VM's SSH host key first. Ansible uses strict host key checking
(`host_key_checking = True`), so a freshly provisioned VM that is not yet in
`~/.ssh/known_hosts` causes `Host key verification failed`. Seed it once before
running the playbook:

```bash
VM_IP="$(terraform -chdir=../terraform/azure-vm output -raw vm_public_ip)"
ssh-keyscan -H "$VM_IP" >> ~/.ssh/known_hosts
```

Alternatively, SSH to the VM once interactively and accept the key prompt.

Optional helper workflow from repo root:

```bash
make deploy-azure
```

Docker itself is installed automatically by the Ansible `docker` role (`get.docker.com`'s official
script), the same way `make azure-vm-docker`/`azure-cicd-setup` does it — no manual Docker install
step needed, and safe to run `azure-cicd-setup` and `deploy-azure` against the same VM in either
order (they used to conflict: apt-installed `docker.io` vs. `get.docker.com`'s `containerd.io`
package, whichever ran second would fail outright).

## 3) Verify deployment

From your machine:

```bash
curl -I http://<vm-public-ip>
```

From the VM:

```bash
ssh <admin_username>@<vm-public-ip>
sudo systemctl status rolling-restarts
sudo docker ps
```

Note: if `docker ps` returns "permission denied while trying to connect to the docker API socket", reconnect your SSH session (or run `newgrp docker`) so group membership is refreshed.

## Known limitation: gen-ai's LLM calls don't currently work on this target

gen-ai's `LLM_PROVIDER=logos` (`https://logos.aet.cit.tum.de/v1`) is **TUM-network-only** —
unreachable from an Azure VM on the public internet. There's also no Ollama instance provisioned
here: `docker-compose.yaml`'s `ollama` service exists but is gated behind the `local-llm` compose
profile, which nothing in this deployment path activates. Everything else (signup/login,
dashboards, mongodb, grafana-lgtm) works — only `/summarize`, `/explain`, `/sentiment`, and `/qa`
are affected. Two real fixes, not yet decided between: actually provision Ollama on the VM (this
is a small burstable ARM64 VM, so a real model may not run acceptably), or point gen-ai's existing
`logos`-shaped code path at a real public OpenAI-compatible endpoint with a real API key.

## Security checklist

- SSH access is restricted to a single source IP. By default `allowed_ssh_cidr` is left unset and Terraform auto-detects the public IP of the machine running `terraform apply`; set it explicitly to pin a fixed CIDR (e.g. an office range)
- Store Ansible secrets with Vault (avoid plaintext `group_vars/all.yml`)
- Keep `terraform.tfvars` and state files out of source control
- Rotate sensitive values before production use
- Stop/deallocate the VM when idle to reduce Azure for Students credit usage
