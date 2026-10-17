from dataclasses import dataclass


@dataclass(frozen=True)
class Principal:
    subject: str
    tenant: str
    roles: frozenset[str]


@dataclass(frozen=True)
class Document:
    id: str
    tenant: str
    text: str


class ServiceError(Exception):
    def __init__(self, status: int, detail: str):
        self.status, self.detail = status, detail
        super().__init__(detail)
