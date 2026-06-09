.PHONY: terraform-init terraform-plan terraform-apply terraform-destroy ansible-inventory ansible-deploy deploy-azure helm-lint helm-template helm-install helm-upgrade helm-deploy helm-destroy

HELM_DIR ?= infra/helm

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
