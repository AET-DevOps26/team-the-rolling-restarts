output "admin_username" {
  description = "Admin username configured on the VM"
  value       = var.admin_username
}

output "resource_group_name" {
  description = "Azure resource group containing deployment resources"
  value       = azurerm_resource_group.main.name
}

output "ssh_command" {
  description = "Convenience SSH command for connecting to the VM"
  value       = "ssh ${var.admin_username}@${azurerm_public_ip.main.ip_address}"
}

output "vm_id" {
  description = "ID of the Linux virtual machine"
  value       = azurerm_linux_virtual_machine.main.id
}

output "vm_name" {
  description = "Name of the Linux virtual machine"
  value       = azurerm_linux_virtual_machine.main.name
}

output "vm_public_ip" {
  description = "Public IP address of the Linux virtual machine"
  value       = azurerm_public_ip.main.ip_address
}
