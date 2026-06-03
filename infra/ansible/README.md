# Azure VM configuration and deployment with Ansible

This directory automates VM configuration and project deployment after Azure infrastructure is created by Terraform.

## Directory structure

- `playbooks/deploy.yml` - entrypoint playbook
- `roles/common` - base packages
- `roles/docker` - Docker engine and compose setup
- `roles/app` - repository checkout, env rendering, service startup
- `inventories/production.ini.example` - inventory template
- `group_vars/all.yml.example` - deployment variables template

## Prerequisites

- Ansible `>= 2.15`
- SSH access to VM created by Terraform
- Git access to this repository from the VM

## Configure inventory and vars

```bash
cd /path/to/repo
./infra/scripts/generate-ansible-inventory.sh
cd infra/ansible
cp group_vars/all.yml.example group_vars/all.yml
```

Then edit:

- `inventories/production.ini` if you need custom host alias/key path
- `group_vars/all.yml` for repository URL/branch and environment variables

Script environment overrides:

- `SSH_KEY_PATH` (default `~/.ssh/id_ed25519`)
- `INVENTORY_HOSTNAME` (default `rolling-restarts`)
- `INVENTORY_PATH` (default `infra/ansible/inventories/production.ini`)
- `TF_DIR` (default `infra/terraform/azure-vm`)

## Secrets handling

- Do not commit plaintext secrets in `group_vars/all.yml`.
- Recommended: encrypt secrets with Ansible Vault (`ansible-vault encrypt group_vars/all.yml`).

## Run deployment

```bash
cd infra/ansible
ansible-playbook playbooks/deploy.yml
```

If your local Ansible version errors on the removed yaml callback plugin, keep
`stdout_callback = default` in `ansible.cfg`.

## What deployment does

1. Installs required system tools
2. Installs Docker + Compose plugin
3. Clones/updates repository under `/opt/rolling-restarts`
4. Renders `infra/.env` from Ansible variables
5. Starts stack from `infra/docker-compose.yaml` via systemd service
6. Verifies app responds on VM localhost

## Post-deployment checks

```bash
ssh <admin_username>@<vm-public-ip>
sudo systemctl status rolling-restarts --no-pager
sudo docker ps
```

If `docker ps` shows a docker socket permission error, reconnect SSH (or run
`newgrp docker`) to refresh group membership.
