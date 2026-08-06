"""从 FastAPI 进程级 Runtime 获取 Facade。"""

from fastapi import Request

from application.errors import runtime_unavailable_error
from application.facades import AgentFacade, KnowledgeFacade
from application.runtime import ApplicationRuntime


def get_runtime(request: Request) -> ApplicationRuntime:
    runtime = getattr(request.app.state, "runtime", None)
    if runtime is None or not getattr(
        request.app.state, "ai_resources_ready", False
    ):
        raise runtime_unavailable_error()
    return runtime


def get_agent_facade(request: Request) -> AgentFacade:
    return get_runtime(request).agent_facade


def get_knowledge_facade(request: Request) -> KnowledgeFacade:
    return get_runtime(request).knowledge_facade
