"""HTTP 依赖提供器；Phase 4 才替换为 bootstrap 创建的真实 Facade。"""

from application.facades import (
    AgentChatCommand,
    AgentChatResult,
    AgentFacade,
    KnowledgeIndexCommand,
    KnowledgeIndexResult,
    KnowledgeFacade,
)
from api.errors import ServiceUnavailableError


class _UnavailableAgentFacade:
    def chat(self, command: AgentChatCommand) -> AgentChatResult:
        raise ServiceUnavailableError(
            code="AI_LLM_UNAVAILABLE",
            message="Phase 3 skeleton has not connected the agent facade",
            retryable=True,
            status_code=503,
        )


class _UnavailableKnowledgeFacade:
    def index_document(self, command: KnowledgeIndexCommand) -> KnowledgeIndexResult:
        raise ServiceUnavailableError(
            code="AI_INTERNAL_ERROR",
            message="Phase 3 skeleton has not connected the knowledge facade",
            retryable=False,
            status_code=500,
        )

    def rebuild(self) -> None:
        raise ServiceUnavailableError(
            code="AI_INTERNAL_ERROR",
            message="Phase 3 skeleton has not connected the knowledge facade",
            retryable=False,
            status_code=500,
        )

    def status(self) -> dict[str, object]:
        raise ServiceUnavailableError(
            code="AI_INTERNAL_ERROR",
            message="Phase 3 skeleton has not connected the knowledge facade",
            retryable=False,
            status_code=500,
        )


_agent_facade: AgentFacade = _UnavailableAgentFacade()
_knowledge_facade: KnowledgeFacade = _UnavailableKnowledgeFacade()


def get_agent_facade() -> AgentFacade:
    return _agent_facade


def get_knowledge_facade() -> KnowledgeFacade:
    return _knowledge_facade
