"""Persistent graph; credentials live in Runtime context, never in graph state/config."""
import asyncio
import hashlib
import json
from dataclasses import dataclass
from contextlib import asynccontextmanager
from typing import TypedDict
from uuid import UUID, uuid5, NAMESPACE_URL
from langgraph.graph import StateGraph, START, END
from langgraph.runtime import Runtime
from langgraph.types import interrupt, Command
from ai_service.application.ports import CommerceGateway
from ai_service.domain import Principal, ServiceError


class ReturnState(TypedDict, total=False):
    workflowId: str
    returnId: str
    tenant: str
    owner: str
    orderId: str
    reason: str
    requestHash: str
    status: str


@dataclass
class RequestContext:
    token: str
    correlation: str


class ReturnApprovalWorkflow:
    """Coordinates proposal, human approval and execution through the Commerce API."""
    def __init__(self, gateway: CommerceGateway, saver, *, operation_timeout: float = 12, max_operations: int = 16):
        if operation_timeout <= 0 or max_operations < 1:
            raise ValueError("Workflow limits must be positive")
        self.gateway, self.lock = gateway, asyncio.Lock()
        self.operation_timeout, self.max_operations = operation_timeout, max_operations
        self.in_flight = 0
        builder = StateGraph(ReturnState, context_schema=RequestContext)
        builder.add_node("propose", self.propose)
        builder.add_node("approval", self.wait_for_approval)
        builder.add_node("execute", self.execute)
        builder.add_edge(START, "propose")
        builder.add_edge("propose", "approval")
        builder.add_edge("approval", "execute")
        builder.add_edge("execute", END)
        self.graph = builder.compile(checkpointer=saver)

    @asynccontextmanager
    async def operation(self):
        # Admission bookkeeping has no await: atomic on this single event loop.
        # Count both executing and queued operations, without an unbounded waiter list.
        if self.in_flight >= self.max_operations:
            raise ServiceError(503, "Workflow capacity exhausted")
        self.in_flight += 1
        try:
            async with asyncio.timeout(self.operation_timeout):
                async with self.lock:
                    yield
        except TimeoutError:
            raise ServiceError(503, "Workflow deadline exceeded") from None
        finally:
            self.in_flight -= 1

    @staticmethod
    def config(workflow_id):
        return {"configurable": {"thread_id": workflow_id}}

    async def propose(self, state: ReturnState, runtime: Runtime[RequestContext]):
        result = await self.gateway.create_return_proposal(
            order_id=state["orderId"], reason=state["reason"], token=runtime.context.token,
            idempotency_key="proposal:" + state["workflowId"], correlation=runtime.context.correlation)
        return {"returnId": result.return_id, "status": "PENDING"}

    async def wait_for_approval(self, state: ReturnState):
        interrupt({"approvalRequired": True, "returnId": state["returnId"]})
        return {"status": "APPROVED"}

    async def execute(self, state: ReturnState, runtime: Runtime[RequestContext]):
        current = await self.gateway.get_return(
            state["returnId"], runtime.context.token, correlation=runtime.context.correlation)
        if current.status != "EXECUTED":
            # Each invocation comes from an explicit MANAGER /approve request.
            # Renew the backend approval, including an expired approval after restart.
            current = await self.gateway.approve_return(
                state["returnId"], current.version, runtime.context.token,
                correlation=runtime.context.correlation)
            if current.status != "EXECUTED":
                await self.gateway.execute_return(
                    state["returnId"], current.version, runtime.context.token,
                    idempotency_key="execute:" + state["returnId"], correlation=runtime.context.correlation)
        return {"status": "EXECUTED"}

    async def create(self, actor: Principal, token: str, body: dict, key: str, correlation: str):
        workflow_id = str(uuid5(NAMESPACE_URL, json.dumps([actor.tenant, actor.subject, key])))
        request_hash = hashlib.sha256(json.dumps(body, sort_keys=True).encode()).hexdigest()
        async with self.operation():
            snapshot = await self.graph.aget_state(self.config(workflow_id))
            if snapshot.values:
                if snapshot.values["requestHash"] != request_hash:
                    raise ServiceError(409, "Idempotency key reused with different content")
                if snapshot.values.get("returnId"):
                    return self.public(snapshot.values)
                # Previous proposal request may have committed remotely before a timeout.
                state = await self.graph.ainvoke(None, self.config(workflow_id), context=RequestContext(token, correlation))
            else:
                state = await self.graph.ainvoke({**body, "workflowId": workflow_id,
                    "tenant": actor.tenant, "owner": actor.subject, "requestHash": request_hash,
                    "status": "CREATING"}, self.config(workflow_id), context=RequestContext(token, correlation))
            return self.public(state)

    async def visible(self, workflow_id: str, actor: Principal):
        try:
            UUID(workflow_id)
        except ValueError:
            raise ServiceError(404, "Workflow not found") from None
        state = (await self.graph.aget_state(self.config(workflow_id))).values
        if not state or state["tenant"] != actor.tenant or (state["owner"] != actor.subject and "MANAGER" not in actor.roles):
            raise ServiceError(404, "Workflow not found")
        return state

    async def get(self, workflow_id: str, actor: Principal):
        return self.public(await self.visible(workflow_id, actor))

    async def approve(self, workflow_id, actor, token, correlation):
        async with self.operation():
            state = await self.visible(workflow_id, actor)
            if "MANAGER" not in actor.roles:
                raise ServiceError(403, "MANAGER role required")
            if state["status"] == "EXECUTED":
                return self.public(state)
            snapshot = await self.graph.aget_state(self.config(workflow_id))
            command = Command(resume=True) if "approval" in snapshot.next else None
            result = await self.graph.ainvoke(command, self.config(workflow_id), context=RequestContext(token, correlation))
            return self.public(result)

    @staticmethod
    def public(state):
        return {k: state[k] for k in ("workflowId", "returnId", "status") if k in state}
