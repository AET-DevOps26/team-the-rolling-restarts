.PHONY: terraform-init terraform-plan terraform-apply terraform-destroy ansible-inventory ansible-deploy deploy-azure

terraform-init:
	cd infra/terraform/azure-vm && terraform init

terraform-plan: terraform-init
	cd infra/terraform/azure-vm && terraform plan

terraform-apply: terraform-init
	cd infra/terraform/azure-vm && terraform apply

terraform-destroy:
	cd infra/terraform/azure-vm && terraform destroy

ansible-inventory:
	./infra/scripts/generate-ansible-inventory.sh

ansible-deploy:
	cd infra/ansible && ansible-playbook playbooks/deploy.yml

deploy-azure: terraform-apply ansible-inventory ansible-deploy
