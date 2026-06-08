# Azure VM Deployment Runbook

This runbook documents end-to-end provisioning and deployment of this project on a Microsoft Azure VM.

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

- Keep `application_ports = [3000]` unless you explicitly need direct ingress on additional ports.
- If needed for debugging, you can temporarily add `8080` for Spring API.

Capture connection details:

```bash
terraform output vm_public_ip
terraform output admin_username
terraform output ssh_command
```

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
curl -I http://<vm-public-ip>:3000
```

From the VM:

```bash
ssh <admin_username>@<vm-public-ip>
sudo systemctl status rolling-restarts
sudo docker ps
```

Note: if `docker ps` returns "permission denied while trying to connect to the docker API socket", reconnect your SSH session (or run `newgrp docker`) so group membership is refreshed.

## Security checklist

- Restrict `allowed_ssh_cidr` in Terraform to your current IP
- Store Ansible secrets with Vault (avoid plaintext `group_vars/all.yml`)
- Keep `terraform.tfvars` and state files out of source control
- Rotate sensitive values before production use
- Stop/deallocate the VM when idle to reduce Azure for Students credit usage
