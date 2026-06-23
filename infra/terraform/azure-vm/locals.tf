locals {
  name_prefix = "${var.project_name}-${var.environment}"

  # SSH source CIDR: use the explicit override when set, otherwise auto-detect the public IP
  # of whoever runs `terraform apply` (home IPs are dynamic, so a hardcoded value goes stale
  # and silently locks you out — port 22 just times out). The data source is only consulted
  # when var.allowed_ssh_cidr is null.
  ssh_cidr = coalesce(var.allowed_ssh_cidr, "${chomp(data.http.my_ip.response_body)}/32")

  common_tags = {
    Environment = var.environment
    ManagedBy   = "Terraform"
    Project     = var.project_name
  }
}
