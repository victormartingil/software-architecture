import asyncio
from ai_service.application.ports import AnswerProvider, DocumentRetriever
from ai_service.domain import Principal, ServiceError


class AnswerService:
    def __init__(self, provider: AnswerProvider, lexical: DocumentRetriever, vector: DocumentRetriever | None = None):
        self.provider, self.lexical, self.vector = provider, lexical, vector
        self.capacity = asyncio.Semaphore(4)

    async def answer(self, actor: Principal, question: str, mode: str = "lexical") -> dict:
        retriever = self.lexical if mode == "lexical" else self.vector
        if retriever is None:
            raise ServiceError(503, "Vector retrieval is not configured")
        async with self.capacity:
            async with asyncio.timeout(12):
                docs = await retriever.search(actor.tenant, question)
                if not docs:
                    return dict(answer="No hay evidencia suficiente.", citations=[], abstained=True,
                                mode=self.provider.mode)
                result = await self.provider.generate(question, docs)
                allowed = {doc.id for doc in docs}
                if not isinstance(result.get("answer"), str) or not isinstance(result.get("abstained"), bool):
                    raise ServiceError(503, "Provider returned an invalid answer")
                citations = result.get("citations")
                if not isinstance(citations, list) or any(c not in allowed for c in citations):
                    raise ServiceError(503, "Provider returned invalid citations")
                if not result["abstained"] and not citations:
                    raise ServiceError(503, "Provider returned an unsupported answer")
                return {**result, "mode": self.provider.mode}
