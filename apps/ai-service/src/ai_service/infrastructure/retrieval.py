import json
import math
import re
import unicodedata
from pathlib import Path
import psycopg
from psycopg.rows import dict_row
from ai_service.domain import Document, ServiceError


def terms(text: str) -> set[str]:
    text = ''.join(c for c in unicodedata.normalize('NFD', text.lower()) if unicodedata.category(c) != 'Mn')
    return set(re.findall(r"[a-z0-9]{3,}", text)) - {"para", "que", "como", "una", "los", "las", "del", "con", "por", "puedo", "cual", "son", "hay"}


class LexicalRetriever:
    def __init__(self, path: Path):
        self.documents = [Document(**d) for d in json.loads(path.read_text())]

    async def search(self, tenant: str, question: str) -> list[Document]:
        query = terms(question)
        # Filter authorization BEFORE scoring. No global cache or cross-tenant candidates.
        ranked = [(len(query & terms(d.text)), d) for d in self.documents if d.tenant == tenant]
        return [d for score, d in sorted(ranked, key=lambda pair: (-pair[0], pair[1].id))[:3] if score > 0]


class VectorRetriever:
    def __init__(self, dsn: str, embedder, dimensions: int):
        if not 1 <= dimensions <= 2000:
            raise ValueError("Invalid embedding dimension")
        self.dsn, self.embedder, self.dimensions = dsn, embedder, dimensions

    async def search(self, tenant: str, question: str) -> list[Document]:
        vector = await self.embedder(question)
        if len(vector) != self.dimensions or not all(math.isfinite(v) for v in vector):
            raise ServiceError(503, "Embedding dimension/value mismatch")
        literal = '[' + ','.join(str(float(v)) for v in vector) + ']'
        async with await psycopg.AsyncConnection.connect(self.dsn, row_factory=dict_row) as conn:
            rows = await (await conn.execute(
                "SELECT id, tenant, text FROM ai_documents WHERE tenant=%s AND dimensions=%s "
                "ORDER BY embedding <=> %s::vector LIMIT 3", (tenant, self.dimensions, literal))).fetchall()
        return [Document(**r) for r in rows]
