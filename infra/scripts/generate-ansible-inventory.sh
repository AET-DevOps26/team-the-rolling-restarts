#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TF_DIR="${TF_DIR:-$ROOT_DIR/infra/terraform/azure-vm}"
INVENTORY_PATH="${INVENTORY_PATH:-$ROOT_DIR/infra/ansible/inventories/production.ini}"
INVENTORY_HOSTNAME="${INVENTORY_HOSTNAME:-rolling-restarts}"
SSH_KEY_PATH="${SSH_KEY_PATH:-$HOME/.ssh/id_ed25519}"

if ! command -v terraform >/dev/null 2>&1; then
  echo "terraform is required but not installed." >&2
  exit 1
fi

if [ ! -d "$TF_DIR" ]; then
  echo "Terraform directory not found: $TF_DIR" >&2
  exit 1
fi

VM_PUBLIC_IP="$(terraform -chdir="$TF_DIR" output -raw vm_public_ip)"
ADMIN_USERNAME="$(terraform -chdir="$TF_DIR" output -raw admin_username)"

mkdir -p "$(dirname "$INVENTORY_PATH")"

cat >"$INVENTORY_PATH" <<EOF
[azure_vm]
${INVENTORY_HOSTNAME} ansible_host=${VM_PUBLIC_IP} ansible_user=${ADMIN_USERNAME} ansible_ssh_private_key_file=${SSH_KEY_PATH}
EOF

# Best-effort: add the VM host key so the first Ansible SSH connection doesn't
# fail on host-key verification. Never fatal — a freshly provisioned VM may not
# accept SSH for a few seconds, and the user can always accept the key manually.
if ! ssh-keygen -F "$VM_PUBLIC_IP" >/dev/null 2>&1; then
  echo "Adding VM host key to ~/.ssh/known_hosts..."
  mkdir -p ~/.ssh && chmod 700 ~/.ssh
  for attempt in 1 2 3 4 5; do
    if keys="$(ssh-keyscan -H "$VM_PUBLIC_IP" 2>/dev/null)" && [ -n "$keys" ]; then
      printf '%s\n' "$keys" >> ~/.ssh/known_hosts
      break
    fi
    echo "  VM not reachable via SSH yet (attempt $attempt/5), retrying in 5s..."
    sleep 5
  done
fi

echo "Generated Ansible inventory at: $INVENTORY_PATH"
echo "Host: $VM_PUBLIC_IP"
echo "User: $ADMIN_USERNAME"
