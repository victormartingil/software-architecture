import httpx
from ai_service.domain import ServiceError


class HttpCommerceGateway:
    def __init__(self, client: httpx.AsyncClient, base_url: str):
        self.client, self.base_url = client, base_url.rstrip('/')

    async def request(self, method, path, token, body=None, key=None, correlation=""):
        headers = {"Authorization": "Bearer " + token, "X-Correlation-ID": correlation}
        if key:
            headers["Idempotency-Key"] = key
        try:
            response = await self.client.request(method, self.base_url + path, json=body,
                                                 headers=headers, timeout=10)
            if response.is_error:
                status = response.status_code if response.status_code in {400,401,403,404,409} else 503
                raise ServiceError(status, "Commerce rejected operation" if status != 503 else "Commerce unavailable")
            return response.json()
        except (httpx.HTTPError, ValueError):
            raise ServiceError(503, "Commerce unavailable") from None
