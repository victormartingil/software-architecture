"""Commerce HTTP boundary: transport and response validation stay out of the graph."""
from uuid import UUID

import httpx
from ai_service.application.ports import ReturnResult
from ai_service.domain import ServiceError


class HttpCommerceGateway:
    def __init__(self, client: httpx.AsyncClient, base_url: str):
        self.client, self.base_url = client, base_url.rstrip('/')

    async def create_return_proposal(self, order_id: str, reason: str, token: str,
                                     idempotency_key: str, correlation: str = "") -> ReturnResult:
        return await self._request("POST", "/api/v1/returns", token,
            {"orderId": order_id, "reason": reason}, idempotency_key, correlation,
            expected_status=201, allowed_states={"PENDING"})

    async def get_return(self, return_id: str, token: str, correlation: str = "") -> ReturnResult:
        return await self._request("GET", "/api/v1/returns/" + return_id, token,
            correlation=correlation, expected_return_id=return_id)

    async def approve_return(self, return_id: str, version: int, token: str,
                             correlation: str = "") -> ReturnResult:
        return await self._request("POST", "/api/v1/returns/" + return_id + "/approve", token,
            {"version": version}, correlation=correlation,
            expected_return_id=return_id, allowed_states={"APPROVED", "EXECUTED"})

    async def execute_return(self, return_id: str, version: int, token: str,
                             idempotency_key: str, correlation: str = "") -> ReturnResult:
        return await self._request("POST", "/api/v1/returns/" + return_id + "/execute", token,
            {"version": version}, idempotency_key, correlation,
            expected_return_id=return_id, allowed_states={"EXECUTED"})

    async def _request(self, method: str, path: str, token: str, body: dict | None = None,
                       idempotency_key: str | None = None, correlation: str = "", *,
                       expected_status: int = 200, expected_return_id: str | None = None,
                       allowed_states: set[str] | None = None) -> ReturnResult:
        headers = {"Authorization": "Bearer " + token, "X-Correlation-ID": correlation}
        if idempotency_key:
            headers["Idempotency-Key"] = idempotency_key
        try:
            response = await self.client.request(method, self.base_url + path, json=body,
                                                 headers=headers, timeout=10)
            if response.is_error:
                status = response.status_code if response.status_code in {400,401,403,404,409} else 503
                raise ServiceError(status, "Commerce rejected operation" if status != 503 else "Commerce unavailable")
            if response.status_code != expected_status:
                raise ValueError("Unexpected response status")
            payload = response.json()
            if not isinstance(payload, dict):
                raise ValueError("Expected return object")
            return_id, version, status = payload.get("id"), payload.get("version"), payload.get("status")
            if not isinstance(return_id, str) or str(UUID(return_id)) != return_id.lower():
                raise ValueError("Invalid return identity")
            if expected_return_id is not None and UUID(return_id) != UUID(expected_return_id):
                raise ValueError("Unexpected return identity")
            if type(version) is not int or version < 0:
                raise ValueError("Invalid return version")
            if not isinstance(status, str) or status not in (allowed_states or {"PENDING", "APPROVED", "EXECUTED"}):
                raise ValueError("Invalid return status")
            return ReturnResult(str(UUID(return_id)), status, version)
        except (httpx.HTTPError, ValueError):
            raise ServiceError(503, "Commerce unavailable") from None
