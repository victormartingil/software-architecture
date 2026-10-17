import time
import asyncio
import jwt
import httpx
from ai_service.domain import Principal, ServiceError


class JwtVerifier:
    """Validates access tokens locally using cached public signing keys; does not log users in."""
    def __init__(self, client: httpx.AsyncClient, issuer: str, audience: str, jwks_url: str):
        self.client, self.issuer, self.audience, self.jwks_url = client, issuer, audience, jwks_url
        self.keys, self.expires, self.lock = {}, 0., asyncio.Lock()

    async def verify(self, token: str) -> Principal:
        try:
            header = jwt.get_unverified_header(token)
            if header.get("alg") != "RS256" or not header.get("kid"):
                raise ServiceError(401, "Invalid credentials")
            async with self.lock:
                if time.monotonic() > self.expires:
                    response = await self.client.get(self.jwks_url, timeout=5)
                    response.raise_for_status()
                    self.keys = {
                        k["kid"]: jwt.PyJWK(k, algorithm="RS256").key
                        for k in response.json()["keys"]
                        if k.get("kty") == "RSA" and k.get("use") == "sig"
                        and k.get("alg") == "RS256"
                    }
                    self.expires = time.monotonic() + 60
            if header["kid"] not in self.keys:
                raise ServiceError(401, "Unknown signing key")
            claims = jwt.decode(token, self.keys[header["kid"]], algorithms=["RS256"],
                issuer=self.issuer, audience=self.audience, options={"require": ["exp", "iss", "aud", "sub", "tenant"]})
            if not isinstance(claims["sub"], str) or not claims["sub"].strip() or not isinstance(claims["tenant"], str) or not claims["tenant"].strip():
                raise ServiceError(401, "Invalid identity")
            realm = claims.get("realm_access", {})
            if not isinstance(realm, dict):
                raise ServiceError(401, "Invalid roles")
            roles = realm.get("roles", [])
            if not isinstance(roles, list) or not all(isinstance(r, str) for r in roles):
                raise ServiceError(401, "Invalid roles")
            if not {"CUSTOMER", "MANAGER"} & set(roles):
                raise ServiceError(403, "Business role required")
            return Principal(claims["sub"], claims["tenant"], frozenset(roles))
        except (jwt.PyJWTError, KeyError, TypeError, ValueError):
            raise ServiceError(401, "Invalid credentials") from None
        except httpx.HTTPError:
            raise ServiceError(503, "Identity provider unavailable") from None
