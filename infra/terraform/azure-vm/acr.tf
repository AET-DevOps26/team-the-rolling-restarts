# Azure Container Registry for the CI/CD pipeline (.github/workflows/deploy-azure.yml).
#
# Kept in the same resource group as the VM so a single `terraform destroy`
# (or deleting the resource group) removes the registry along with everything
# else - no lingering, billable resources on a student subscription.

resource "azurerm_container_registry" "main" {
  name                = local.acr_name
  resource_group_name = azurerm_resource_group.main.name
  location            = azurerm_resource_group.main.location
  sku                 = var.acr_sku
  admin_enabled       = false
  tags                = local.common_tags
}

# Optional: grant the CI/CD service principal the access it needs so a fresh
# `terraform apply` is fully turnkey (no manual role assignments afterwards).
# Leave `deploy_principal_id` empty to manage these manually.
#
# AcrPush (includes pull) covers the build job's push and the short-lived token
# the deploy job uses to pull on the VM.
resource "azurerm_role_assignment" "acr_push" {
  count = var.deploy_principal_id == "" ? 0 : 1

  scope                = azurerm_container_registry.main.id
  role_definition_name = "AcrPush"
  principal_id         = var.deploy_principal_id
}

# Virtual Machine Contributor on the resource group lets the deploy job run
# `az vm run-command` against the VM.
resource "azurerm_role_assignment" "deploy_vm" {
  count = var.deploy_principal_id == "" ? 0 : 1

  scope                = azurerm_resource_group.main.id
  role_definition_name = "Virtual Machine Contributor"
  principal_id         = var.deploy_principal_id
}
