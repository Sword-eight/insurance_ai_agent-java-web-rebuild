"""知识库内部 API 契约。"""

from typing import Literal
from uuid import UUID

from pydantic import BaseModel, ConfigDict, Field, StringConstraints
from typing_extensions import Annotated


OriginalFilename = Annotated[
    str,
    StringConstraints(strip_whitespace=True, min_length=1, max_length=255),
]


class KnowledgeIndexMetadata(BaseModel):
    model_config = ConfigDict(extra="forbid")

    requestId: UUID
    documentId: UUID
    originalFilename: OriginalFilename
    sha256: str = Field(pattern=r"^[A-Fa-f0-9]{64}$")


class KnowledgeIndexData(BaseModel):
    model_config = ConfigDict(extra="forbid")

    requestId: UUID
    documentId: UUID
    indexStatus: Literal["INDEXED"]
