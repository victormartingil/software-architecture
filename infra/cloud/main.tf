terraform {
  required_version = "= 1.10.5"
  required_providers {
    google = {
      source  = "hashicorp/google"
      version = "6.30.0"
    }
  }
}

provider "google" {
  project = var.project_id
  region  = var.region
}

resource "google_compute_network" "reference" {
  name                    = "${var.name}-vpc"
  auto_create_subnetworks = false
}

resource "google_compute_subnetwork" "reference" {
  name                     = "${var.name}-subnet"
  region                   = var.region
  network                  = google_compute_network.reference.id
  ip_cidr_range            = "10.40.0.0/20"
  private_ip_google_access = true
  secondary_ip_range {
    range_name    = "pods"
    ip_cidr_range = "10.44.0.0/16"
  }
  secondary_ip_range {
    range_name    = "services"
    ip_cidr_range = "10.48.0.0/20"
  }
}

resource "google_compute_router" "reference" {
  name    = "${var.name}-router"
  region  = var.region
  network = google_compute_network.reference.id
}

resource "google_compute_router_nat" "reference" {
  name                               = "${var.name}-nat"
  router                             = google_compute_router.reference.name
  region                             = var.region
  nat_ip_allocate_option             = "AUTO_ONLY"
  source_subnetwork_ip_ranges_to_nat = "ALL_SUBNETWORKS_ALL_IP_RANGES"
}

resource "google_artifact_registry_repository" "images" {
  location      = var.region
  repository_id = var.name
  format        = "DOCKER"
}

resource "google_service_account" "nodes" {
  account_id   = "${var.name}-nodes"
  display_name = "Reference GKE nodes"
}

resource "google_project_iam_member" "node_roles" {
  for_each = toset(["roles/container.defaultNodeServiceAccount"])
  project  = var.project_id
  role     = each.key
  member   = "serviceAccount:${google_service_account.nodes.email}"
}

resource "google_artifact_registry_repository_iam_member" "node_image_reader" {
  project    = var.project_id
  location   = var.region
  repository = google_artifact_registry_repository.images.repository_id
  role       = "roles/artifactregistry.reader"
  member     = "serviceAccount:${google_service_account.nodes.email}"
}

resource "google_container_cluster" "reference" {
  name                = var.name
  location            = var.region
  enable_autopilot    = true
  deletion_protection = true
  network             = google_compute_network.reference.id
  subnetwork          = google_compute_subnetwork.reference.id
  release_channel { channel = "REGULAR" }
  ip_allocation_policy {
    cluster_secondary_range_name  = "pods"
    services_secondary_range_name = "services"
  }
  private_cluster_config {
    enable_private_nodes    = true
    enable_private_endpoint = false
    master_ipv4_cidr_block  = "172.16.0.0/28"
  }
  master_authorized_networks_config {
    cidr_blocks {
      cidr_block   = var.operator_cidr
      display_name = "operator"
    }
  }
  cluster_autoscaling {
    auto_provisioning_defaults {
      service_account = google_service_account.nodes.email
      oauth_scopes    = ["https://www.googleapis.com/auth/cloud-platform"]
    }
  }
  workload_identity_config { workload_pool = "${var.project_id}.svc.id.goog" }
  depends_on = [google_project_iam_member.node_roles, google_artifact_registry_repository_iam_member.node_image_reader, google_compute_router_nat.reference]
}
