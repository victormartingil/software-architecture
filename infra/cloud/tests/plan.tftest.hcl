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
  assert {
    condition     = length(google_project_iam_member.node_roles) == 4
    error_message = "Node permissions must remain bounded."
  }
}

run "reject_open_control_plane" {
  command = plan
  variables { operator_cidr = "0.0.0.0/0" }
  expect_failures = [var.operator_cidr]
}
