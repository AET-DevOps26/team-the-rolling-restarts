variable "admin_username" {
  description = "Admin username used for SSH access on the Azure VM"
  type        = string
  default     = "azureuser"
}

variable "allowed_ssh_cidr" {
  description = "CIDR block allowed to access SSH port 22 (use your public IP/32)"
  type        = string
}

variable "application_ports" {
  description = "TCP ports opened for application access (least-privilege default: web-client only)"
  type        = list(number)
  default     = [3000]
}

variable "environment" {
  description = "Deployment environment name (for tagging and resource names)"
  type        = string
  default     = "dev"
}

variable "location" {
  description = "Azure region where resources will be created"
  type        = string
  default     = "westeurope"
}

variable "project_name" {
  description = "Project name used in resource naming"
  type        = string
  default     = "rolling-restarts"
}

variable "public_key_path" {
  description = "Path to an SSH public key file used for VM authentication"
  type        = string
}

variable "resource_group_name" {
  description = "Name of the Azure resource group to create"
  type        = string
  default     = "rg-rolling-restarts-dev"
}

variable "subnet_cidr" {
  description = "Subnet CIDR for the VM subnet"
  type        = string
  default     = "10.42.1.0/24"
}

variable "subscription_id" {
  description = "Azure subscription ID where resources will be managed"
  type        = string
}

variable "vm_size" {
  description = "Azure VM size"
  type        = string
  default     = "Standard_B2s"
}

variable "vm_image_sku" {
  description = "VM image SKU. Use server-arm64 for Arm64 VM sizes."
  type        = string
  default     = "server"
}

variable "vnet_cidr" {
  description = "Virtual network CIDR range"
  type        = string
  default     = "10.42.0.0/16"
}
