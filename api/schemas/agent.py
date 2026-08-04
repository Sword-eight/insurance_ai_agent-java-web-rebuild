"""同步 Agent 聊天契约。"""

from typing import Literal
from uuid import UUID

from pydantic import BaseModel, ConfigDict, Field, StringConstraints, model_validator
from typing_extensions import Annotated


TrimmedContent = Annotated[
    str,
    StringConstraints(strip_whitespace=True, min_length=1, max_length=4000),
]


class HistoryMessage(BaseModel):
    model_config = ConfigDict(extra="forbid")

    role: Literal["user", "assistant"]
    content: TrimmedContent


class AgentChatRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    requestId: UUID
    sessionId: UUID
    message: TrimmedContent
    history: list[HistoryMessage] = Field(max_length=10)

    @model_validator(mode="after")
    def validate_history_budget_and_pairs(self) -> "AgentChatRequest":
        if sum(len(item.content) for item in self.history) > 12000:
            raise ValueError("history content must not exceed 12000 characters")
        if len(self.history) % 2 != 0:
            raise ValueError("history must contain complete user/assistant pairs")
        for index, item in enumerate(self.history):
            expected_role = "user" if index % 2 == 0 else "assistant"
            if item.role != expected_role:
                raise ValueError("history must alternate user and assistant roles")
        return self


class AgentChatData(BaseModel):
    model_config = ConfigDict(extra="forbid")

    requestId: UUID
    answer: str
    sources: list[dict[str, object]]
    durationMs: int = Field(ge=0)
