# Azure VM with Terraform

This directory provisions Azure infrastructure for deploying the team project.

## What it creates

- Resource group
- Virtual network and subnet
- Network security group with SSH + app port rules
- Public IP and network interface
- Ubuntu Linux VM (default size: `Standard_B2s`)
- Azure Container Registry (default SKU: `Basic`) used by the deploy-azure CI/CD
  workflow — kept in the same resource group so teardown removes it too

Because `providers.tf` disables automatic provider registration, register the
container-registry provider once per subscription before the first apply:

```bash
az provider register --namespace Microsoft.ContainerRegistry
```

Default app ingress policy is least-privilege (`application_ports = [3000]`).
Only add extra ports (for example `8080`) when needed.

## VM size and image architecture

Keep VM size architecture aligned with image SKU:

- x64 VM sizes -> `vm_image_sku = "server"`
- Arm64 VM sizes (for example `Standard_B2ps_v2`) -> `vm_image_sku = "server-arm64"`

## Prerequisites

- Terraform `>= 1.8`
- Azure CLI authenticated (`az login`)
- Access to target Azure subscription
- SSH public key for VM login

## Authentication

Terraform uses Azure CLI auth by default via the `azurerm` provider.
Make sure you are logged in and the right subscription is selected:

```bash
az login
az account set --subscription "<subscription-id>"
```

For constrained/student subscriptions, this project disables broad automatic
resource-provider registration in `providers.tf` and expects only required
providers (`Microsoft.Resources`, `Microsoft.Network`, `Microsoft.Compute`) to
be available.

## Usage

```bash
cd infra/terraform/azure-vm
cp terraform.tfvars.example terraform.tfvars
# edit terraform.tfvars values
terraform init
terraform plan
terraform apply
```

Retrieve VM connection info:

```bash
terraform output vm_public_ip
terraform output ssh_command
```

## Security notes

- Limit `allowed_ssh_cidr` to your own public IP (`x.x.x.x/32`)
- Keep `terraform.tfvars` out of version control when it contains sensitive values
- Treat Terraform state as sensitive operational data
- Stop/deallocate the VM when not in use to preserve student credits

## Cost control & destroy

For student subscriptions, minimise spend:

```bash
# From the repository root - pause/resume the VM (compute billing stops):
make azure-stop
make azure-start
```

Tear down everything (VM + ACR + networking + resource group):

```bash
terraform destroy            # clean teardown, keeps state in sync
# or, from the repository root, a state-independent nuke:
make azure-nuke              # az group delete (override AZURE_RG=... if needed)
```

A one-click teardown is also available via the
`Destroy Azure resources` GitHub Actions workflow (see
`docs/cicd-azure-deploy.md`).
