"""FastAPI 进程级 Application Runtime。"""

from collections.abc import Awaitable, Callable
from dataclasses import dataclass, field
import inspect

from application.facades import AgentFacade, KnowledgeFacade


CloseCallback = Callable[[], object | Awaitable[object]]


@dataclass
class ApplicationRuntime:
    agent_facade: AgentFacade
    knowledge_facade: KnowledgeFacade
    rag_engine: str = "langchain"
    close_callbacks: tuple[CloseCallback, ...] = field(default_factory=tuple)

    async def close(self) -> None:
        for callback in reversed(self.close_callbacks):
            result = callback()
            if inspect.isawaitable(result):
                await result
