import asyncio
from typing import Protocol
from ai_service.domain import Document


class AnswerProvider(Protocol):
    """Produces a structured answer from documents already authorized for the actor."""
    mode: str
    async def generate(self, question: str, documents: list[Document], *,
                       deadline: asyncio.Timeout | None = None) -> dict: ...


class DocumentRetriever(Protocol):
    """Finds relevant evidence inside one tenant before any generation."""
    async def search(self, tenant: str, question: str) -> list[Document]: ...


class CommerceGateway(Protocol):
    async def request(self, method: str, path: str, token: str, body: dict | None = None,
                      key: str | None = None, correlation: str = "") -> dict: ...
