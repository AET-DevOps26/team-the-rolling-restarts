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

```bash
# Build and push arm64 images (or multi-arch for both K8s and VM)
make push-images IMAGE_TAG=<tag> PLATFORM=linux/amd64,linux/arm64

# Or arm64-only if you only target the VM
make push-images IMAGE_TAG=<tag> PLATFORM=linux/arm64
```

See [Cross-architecture builds](deployment-testing.md#cross-architecture-builds) for the one-time QEMU/buildx setup.

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

## Security checklist

- SSH access is restricted to a single source IP. By default `allowed_ssh_cidr` is left unset and Terraform auto-detects the public IP of the machine running `terraform apply`; set it explicitly to pin a fixed CIDR (e.g. an office range)
- Store Ansible secrets with Vault (avoid plaintext `group_vars/all.yml`)
- Keep `terraform.tfvars` and state files out of source control
- Rotate sensitive values before production use
- Stop/deallocate the VM when idle to reduce Azure for Students credit usage
