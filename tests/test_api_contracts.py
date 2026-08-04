from uuid import uuid4

import pytest
from pydantic import ValidationError

from api.config import ApiSettings
from api.schemas.agent import AgentChatRequest
from api.schemas.knowledge import KnowledgeIndexMetadata


def _chat_payload() -> dict[str, object]:
    return {
        "requestId": str(uuid4()),
        "sessionId": str(uuid4()),
        "message": " 等待期一般有多久？ ",
        "history": [
            {"role": "user", "content": "我想了解医疗险"},
            {"role": "assistant", "content": "你想了解哪一方面？"},
        ],
    }


def test_chat_contract_accepts_and_trims_valid_request() -> None:
    request = AgentChatRequest.model_validate(_chat_payload())

    assert request.message == "等待期一般有多久？"
    assert len(request.history) == 2


@pytest.mark.parametrize(
    ("field", "value"),
    [
        ("requestId", "not-a-uuid"),
        ("message", "   "),
        ("message", "x" * 4001),
    ],
)
def test_chat_contract_rejects_invalid_scalar_fields(field: str, value: str) -> None:
    payload = _chat_payload()
    payload[field] = value

    with pytest.raises(ValidationError):
        AgentChatRequest.model_validate(payload)


def test_chat_contract_rejects_incomplete_history_pair() -> None:
    payload = _chat_payload()
    payload["history"] = [{"role": "user", "content": "只有一条"}]

    with pytest.raises(ValidationError, match="complete user/assistant pairs"):
        AgentChatRequest.model_validate(payload)


def test_chat_contract_rejects_history_over_total_budget() -> None:
    payload = _chat_payload()
    payload["history"] = [
        {"role": "user", "content": "u" * 3001},
        {"role": "assistant", "content": "a" * 3001},
    ] * 2

    with pytest.raises(ValidationError, match="12000"):
        AgentChatRequest.model_validate(payload)


def test_knowledge_metadata_matches_frozen_shape() -> None:
    metadata = KnowledgeIndexMetadata.model_validate(
        {
            "requestId": str(uuid4()),
            "documentId": str(uuid4()),
            "originalFilename": "保险条款.pdf",
            "sha256": "a" * 64,
        }
    )

    assert metadata.originalFilename == "保险条款.pdf"


@pytest.mark.parametrize("port", ["0", "65536", "not-a-number"])
def test_api_settings_reject_invalid_port(monkeypatch: pytest.MonkeyPatch, port: str) -> None:
    monkeypatch.setenv("AI_SERVICE_PORT", port)

    with pytest.raises(ValueError, match="AI_SERVICE_PORT"):
        ApiSettings.from_env()
