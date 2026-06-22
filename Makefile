.PHONY: terraform-init terraform-plan terraform-apply terraform-destroy ansible-inventory ansible-deploy deploy-azure azure-stop azure-start azure-nuke helm-lint helm-template helm-install helm-upgrade helm-deploy helm-destroy

HELM_DIR ?= infra/helm
TF_DIR ?= infra/terraform/azure-vm

# Resource group used by azure-nuke. Defaults to the Terraform default; override
# (make azure-nuke AZURE_RG=...) if you changed resource_group_name.
AZURE_RG ?= rg-rolling-restarts-dev

terraform-init:
	cd infra/terraform/azure-vm && terraform init

terraform-plan: terraform-init
	cd infra/terraform/azure-vm && terraform plan

terraform-apply: terraform-init
	cd infra/terraform/azure-vm && terraform apply

terraform-destroy: terraform-init
	cd infra/terraform/azure-vm && terraform destroy

ansible-inventory:
	./infra/scripts/generate-ansible-inventory.sh

ansible-deploy:
	cd infra/ansible && ansible-playbook playbooks/deploy.yml

deploy-azure: terraform-apply ansible-inventory ansible-deploy

# --- Cost control -----------------------------------------------------------
# Pause the VM to stop compute billing (disk + static IP still incur a small
# cost). Resume later with azure-start. Requires existing Terraform state.
azure-stop:
	cd $(TF_DIR) && az vm deallocate \
		--resource-group "$$(terraform output -raw resource_group_name)" \
		--name "$$(terraform output -raw vm_name)"

azure-start:
	cd $(TF_DIR) && az vm start \
		--resource-group "$$(terraform output -raw resource_group_name)" \
		--name "$$(terraform output -raw vm_name)"

# Nuke everything by deleting the whole resource group (VM + ACR + networking).
# State-independent fallback for when Terraform state is unavailable; prefer
# `make terraform-destroy` for a clean teardown that keeps state in sync.
azure-nuke:
	az group delete --name "$(AZURE_RG)" --yes

helm-lint:
	$(MAKE) -C $(HELM_DIR) helm-lint

helm-template:
	$(MAKE) -C $(HELM_DIR) helm-template

helm-install:
	$(MAKE) -C $(HELM_DIR) helm-install

helm-upgrade:
	$(MAKE) -C $(HELM_DIR) helm-upgrade

helm-deploy:
	$(MAKE) -C $(HELM_DIR) helm-deploy

helm-destroy:
	$(MAKE) -C $(HELM_DIR) helm-destroy
