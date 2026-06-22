# Azure Container Registry for the CI/CD pipeline (.github/workflows/deploy-azure.yml).
#
# Kept in the same resource group as the VM so a single `terraform destroy`
# (or deleting the resource group) removes the registry along with everything
# else - no lingering, billable resources on a student subscription.

resource "azurerm_container_registry" "main" {
  name                = "${lower(replace(local.name_prefix, "-", ""))}${random_string.name_suffix.result}"
  resource_group_name = azurerm_resource_group.main.name
  location            = azurerm_resource_group.main.location
  sku                 = var.acr_sku
  admin_enabled       = false
  tags                = local.common_tags
}

# Optional: grant the CI/CD service principal push/pull access on the registry.
# AcrPush includes pull, which covers both the build job (push) and the
# short-lived token the deploy job uses on the VM (pull).
# Leave `deploy_principal_id` empty to manage this role assignment manually.
resource "azurerm_role_assignment" "acr_push" {
  count = var.deploy_principal_id == "" ? 0 : 1

  scope                = azurerm_container_registry.main.id
  role_definition_name = "AcrPush"
  principal_id         = var.deploy_principal_id
}
