mock_provider "google" {}


variables {
  project_id              = "synthetic-project"
  operator_cidr           = "192.0.2.1/32"
  database_endpoint       = "postgres.example.invalid"
  kafka_bootstrap_servers = "kafka.example.invalid:9093"
}

run "secure_foundation" {
  command = plan
  assert {
    condition     = google_container_cluster.reference.enable_autopilot && google_container_cluster.reference.deletion_protection
    error_message = "Autopilot and deletion protection must be enabled."
  }
  assert {
    condition     = google_container_cluster.reference.private_cluster_config[0].enable_private_nodes
    error_message = "Nodes must use private addresses."
  }
  assert {
    condition     = !google_compute_network.reference.auto_create_subnetworks
    error_message = "Subnets must be explicit."
  }
  # Terraform 1.10 plan leaves computed SA emails unknown; identity wiring is reviewed separately.
  assert {
    condition     = toset([for binding in google_project_iam_member.node_roles : binding.role]) == toset(["roles/container.defaultNodeServiceAccount"]) && alltrue([for binding in google_project_iam_member.node_roles : binding.project == var.project_id])
    error_message = "Nodes need the GKE minimum role, without unrelated project permissions."
  }
  assert {
    condition     = google_artifact_registry_repository_iam_member.node_image_reader.role == "roles/artifactregistry.reader" && google_artifact_registry_repository_iam_member.node_image_reader.repository == google_artifact_registry_repository.images.repository_id
    error_message = "Image read access must be scoped to the reference repository."
  }
}

run "reject_ipv6_operator" {
  command = plan
  variables { operator_cidr = "2001:db8::/32" }
  expect_failures = [var.operator_cidr]
}

run "reject_invalid_operator" {
  command = plan
  variables { operator_cidr = "999.0.0.1/32" }
  expect_failures = [var.operator_cidr]
}

run "reject_open_control_plane" {
  command = plan
  variables { operator_cidr = "0.0.0.0/0" }
  expect_failures = [var.operator_cidr]
}
