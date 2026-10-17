import asyncio
from dataclasses import dataclass
from typing import Literal
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


@dataclass(frozen=True)
class ReturnResult:
    return_id: str
    status: Literal["PENDING", "APPROVED", "EXECUTED"]
    version: int


class CommerceGateway(Protocol):
    """Return operations; credentials are supplied per invocation, never persisted."""
    async def create_return_proposal(self, order_id: str, reason: str, token: str,
                                     idempotency_key: str, correlation: str = "") -> ReturnResult: ...
    async def get_return(self, return_id: str, token: str, correlation: str = "") -> ReturnResult: ...
    async def approve_return(self, return_id: str, version: int, token: str,
                             correlation: str = "") -> ReturnResult: ...
    async def execute_return(self, return_id: str, version: int, token: str,
                             idempotency_key: str, correlation: str = "") -> ReturnResult: ...
