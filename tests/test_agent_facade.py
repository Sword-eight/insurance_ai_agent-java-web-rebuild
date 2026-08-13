from uuid import uuid4

import pytest
from langchain_core.messages import AIMessage, HumanMessage

from application.agent_facade import DefaultAgentFacade
from application.errors import ApplicationError
from application.facades import AgentChatCommand, HistoryItem
from application.request_registry import RequestRegistry


class RecordingGraphBuilder:
    def __init__(self, *, result=None, error: Exception | None = None) -> None:
        self.calls: list[dict[str, object]] = []
        self.result = result or {"messages": [AIMessage(content="真实回答")]}
        self.error = error

    def invoke(self, **kwargs):
        self.calls.append(kwargs)
        if self.error is not None:
            raise self.error
        return self.result


class SequenceClock:
    def __init__(self, *values: float) -> None:
        self._values = iter(values)

    def __call__(self) -> float:
        return next(self._values)


def _command(*, request_id=None, message: str = "当前问题") -> AgentChatCommand:
    return AgentChatCommand(
        request_id=request_id or uuid4(),
        session_id=uuid4(),
        message=message,
        history=(
            HistoryItem(role="user", content="历史问题"),
            HistoryItem(role="assistant", content="历史回答"),
        ),
    )


def test_agent_facade_converts_history_and_returns_real_answer() -> None:
    graph = RecordingGraphBuilder()
    facade = DefaultAgentFacade(
        graph_builder=graph,
        registry=RequestRegistry(),
        clock=SequenceClock(10.0, 11.234),
    )
    command = _command()

    result = facade.chat(command)

    assert result.answer == "真实回答"
    assert result.sources == ()
    assert result.duration_ms == 1234
    assert result.request_id == command.request_id
    call = graph.calls[0]
    assert call["session_id"] == str(command.session_id)
    assert call["execution_id"] == str(command.request_id)
    history = call["history_messages"]
    assert isinstance(history[0], HumanMessage)
    assert isinstance(history[1], AIMessage)
    assert [message.content for message in history] == ["历史问题", "历史回答"]


def test_agent_facade_reuses_cached_result() -> None:
    graph = RecordingGraphBuilder()
    facade = DefaultAgentFacade(
        graph_builder=graph,
        registry=RequestRegistry(),
    )
    command = _command()

    first = facade.chat(command)
    second = facade.chat(command)

    assert second == first
    assert len(graph.calls) == 1


def test_agent_facade_maps_and_deduplicates_real_retrieval_sources() -> None:
    graph = RecordingGraphBuilder(result={
        "messages": [AIMessage(content="answer")],
        "retrieved_docs": [
            {
                "content": " waiting period clause " * 40,
                "source_name": "terms.pdf",
                "source_page": 3,
                "similarity_score": 0.91,
            },
            {
                "content": " waiting period clause " * 40,
                "source_name": "terms.pdf",
                "source_page": 3,
                "similarity_score": 0.91,
            },
            {
                "content": "premium calculation only",
                "source_name": "",
                "source_page": 0,
                "similarity_score": 0.5,
            },
        ],
    })
    facade = DefaultAgentFacade(
        graph_builder=graph,
        registry=RequestRegistry(),
    )

    result = facade.chat(_command())

    assert len(result.sources) == 1
    assert result.sources[0]["documentName"] == "terms.pdf"
    assert result.sources[0]["page"] == 3
    assert result.sources[0]["score"] == 0.91
    assert len(result.sources[0]["snippet"]) == 500


def test_agent_facade_keeps_premium_only_result_sources_empty() -> None:
    graph = RecordingGraphBuilder(result={
        "messages": [AIMessage(content="premium answer")],
        "tool_results": [{"tool_name": "premium_calculator"}],
        "retrieved_docs": [],
    })
    facade = DefaultAgentFacade(
        graph_builder=graph,
        registry=RequestRegistry(),
    )

    assert facade.chat(_command()).sources == ()


def test_agent_facade_rejects_conflicting_payload_for_same_request_id() -> None:
    graph = RecordingGraphBuilder()
    facade = DefaultAgentFacade(
        graph_builder=graph,
        registry=RequestRegistry(),
    )
    request_id = uuid4()
    facade.chat(_command(request_id=request_id, message="first"))

    with pytest.raises(ApplicationError) as captured:
        facade.chat(_command(request_id=request_id, message="second"))

    assert captured.value.code == "AI_REQUEST_CONFLICT"
    assert len(graph.calls) == 1


def test_agent_facade_maps_timeout_and_caches_failure() -> None:
    graph = RecordingGraphBuilder(error=TimeoutError())
    facade = DefaultAgentFacade(
        graph_builder=graph,
        registry=RequestRegistry(),
    )
    command = _command()

    for _ in range(2):
        with pytest.raises(ApplicationError) as captured:
            facade.chat(command)
        assert captured.value.code == "AI_LLM_TIMEOUT"

    assert len(graph.calls) == 1


def test_agent_facade_rejects_empty_or_missing_ai_answer() -> None:
    graph = RecordingGraphBuilder(result={"messages": [AIMessage(content=" ")]})
    facade = DefaultAgentFacade(
        graph_builder=graph,
        registry=RequestRegistry(),
    )

    with pytest.raises(ApplicationError) as captured:
        facade.chat(_command())

    assert captured.value.code == "AI_INTERNAL_ERROR"
    assert captured.value.safe_message == "agent execution failed"
