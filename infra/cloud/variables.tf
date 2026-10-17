variable "project_id" {
  type        = string
  description = "Existing project with billing and required APIs enabled externally."
}
variable "region" {
  type    = string
  default = "europe-west1"
}
variable "name" {
  type    = string
  default = "architecture-ref"
  validation {
    condition     = can(regex("^[a-z][a-z0-9-]{2,19}$", var.name))
    error_message = "Use 3..20 lowercase characters for GCP resource names."
  }
}
variable "operator_cidr" {
  type        = string
  description = "Trusted operator public /32; never expose the control plane to all IPs."
  validation {
    condition     = can(cidrhost(var.operator_cidr, 0)) && endswith(var.operator_cidr, "/32")
    error_message = "Provide a valid IPv4 /32 operator CIDR."
  }
}
variable "database_endpoint" {
  type        = string
  description = "External managed database endpoint; no credentials in Terraform."
}
variable "kafka_bootstrap_servers" {
  type        = string
  description = "External managed Kafka endpoint; configure TLS/SASL in deployment secrets."
}
