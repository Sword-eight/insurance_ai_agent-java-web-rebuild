"""HTTP 层与现有 AI 核心之间的端口；Phase 4 才提供真实实现。"""

from dataclasses import dataclass
from typing import BinaryIO, Literal, Protocol
from uuid import UUID


@dataclass(frozen=True)
class HistoryItem:
    role: Literal["user", "assistant"]
    content: str


@dataclass(frozen=True)
class AgentChatCommand:
    request_id: UUID
    session_id: UUID
    message: str
    history: tuple[HistoryItem, ...]


@dataclass(frozen=True)
class AgentChatResult:
    request_id: UUID
    answer: str
    sources: tuple[dict[str, object], ...]
    duration_ms: int


class AgentFacade(Protocol):
    def chat(self, command: AgentChatCommand) -> AgentChatResult:
        """执行同步聊天；真实实现属于 Phase 4。"""


@dataclass(frozen=True)
class KnowledgeIndexCommand:
    request_id: UUID
    document_id: UUID
    original_filename: str
    sha256: str
    content: BinaryIO


@dataclass(frozen=True)
class KnowledgeIndexResult:
    request_id: UUID
    document_id: UUID
    index_status: Literal["INDEXED"]


class KnowledgeFacade(Protocol):
    def index_document(self, command: KnowledgeIndexCommand) -> KnowledgeIndexResult:
        """同步索引文档；真实实现属于 Phase 4/10。"""

    def rebuild(self) -> None:
        """同步重建；真实实现属于 Phase 4/10。"""

    def status(self) -> dict[str, object]:
        """返回不含本机路径的加载状态。"""
