output "cluster_name" { value = google_container_cluster.reference.name }
output "image_repository" {
  value = "${var.region}-docker.pkg.dev/${var.project_id}/${google_artifact_registry_repository.images.repository_id}"
}
output "external_dependencies" {
  value = {
    database = var.database_endpoint
    kafka    = var.kafka_bootstrap_servers
  }
}
