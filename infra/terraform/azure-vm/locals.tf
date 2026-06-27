locals {
  name_prefix = "${var.project_name}-${var.environment}"

  # SSH source CIDR: use the explicit override when set, otherwise auto-detect the public IP
  # of whoever runs `terraform apply` (home IPs are dynamic, so a hardcoded value goes stale
  # and silently locks you out — port 22 just times out). The data source is only consulted
  # when var.allowed_ssh_cidr is null.
  ssh_cidr = coalesce(var.allowed_ssh_cidr, "${chomp(data.http.my_ip.response_body)}/32")

  # Deterministic ACR name so it stays identical across destroy/recreate cycles
  # (the GitHub ACR_NAME / ACR_LOGIN_SERVER variables never need updating).
  # The suffix is derived from the subscription ID: stable per subscription and
  # globally unique enough to avoid ACR's cross-tenant name collisions, with no
  # random component. Override with var.acr_name to pin an exact name.
  acr_name = var.acr_name != "" ? var.acr_name : "${lower(replace(local.name_prefix, "-", ""))}${substr(sha1(var.subscription_id), 0, 8)}"

  common_tags = {
    Environment = var.environment
    ManagedBy   = "Terraform"
    Project     = var.project_name
  }
}
