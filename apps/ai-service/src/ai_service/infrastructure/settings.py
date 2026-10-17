from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    oidc_issuer: str = "http://localhost:8088/realms/reference"
    oidc_jwks_url: str = "http://keycloak:8080/realms/reference/protocol/openid-connect/certs"
    oidc_audience: str = "reference-api"
    commerce_url: str = "http://commerce-api:8080"
    workflow_db: str = "/data/workflows.sqlite"
    ai_provider: str = "fake"
    openai_api_key: str = ""
    openai_model: str = ""
    ai_live_budget_usd: float = 0
    ai_max_requests: int = 0
    otel_exporter_otlp_endpoint: str = ""
    embedding_model: str = ""
    embedding_dimensions: int = 3
    vector_dsn: str = ""
