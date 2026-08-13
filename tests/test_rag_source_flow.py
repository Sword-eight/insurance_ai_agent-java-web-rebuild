from concurrent.futures import ThreadPoolExecutor
from typing import Any

from langchain_core.messages import AIMessage, ToolMessage
import numpy as np
import pytest

from graph.graph_builder import AgentGraphBuilder
from rag.base_retriever import BaseRetriever, RetrievalDocument, RetrievalResult
from services.retrieval_service import RetrievalService
from tools.insurance_rag_tool import InsuranceRAGTool


class QueryRetriever(BaseRetriever):
    def retrieve(
        self,
        query: str,
        top_k: int = 5,
        score_threshold: float = 0.0,
    ) -> RetrievalResult:
        documents = [
            RetrievalDocument(
                content=f"clause for {query}",
                source_name=f"{query}.pdf",
                source_page=2,
                similarity_score=np.float32(0.88),
                engine="memory",
                raw_metadata={"source": f"{query}.pdf", "page": 1},
            )
        ]
        return RetrievalResult(
            query=query,
            documents=documents,
            total_found=1,
            engine="memory",
        )


class SequenceLLM:
    model_name = "offline-rag-source-llm"

    def __init__(self, responses: list[AIMessage]) -> None:
        self._responses = responses

    def bind_tools(self, tools: list[Any]) -> "SequenceLLM":
        return self

    def invoke(self, messages: list[Any]) -> AIMessage:
        return self._responses.pop(0)


class ConcurrentLLM:
    model_name = "offline-concurrent-rag-source-llm"

    def bind_tools(self, tools: list[Any]) -> "ConcurrentLLM":
        return self

    def invoke(self, messages: list[Any]) -> AIMessage:
        if any(isinstance(message, ToolMessage) for message in messages):
            return AIMessage(content="final answer")
        query = messages[-1].content
        return _rag_call(query, f"rag-{query}")


def _rag_call(query: str, call_id: str) -> AIMessage:
    return AIMessage(
        content="",
        tool_calls=[{
            "name": "insurance_rag_search",
            "args": {"query": query},
            "id": call_id,
            "type": "tool_call",
        }],
    )


def test_rag_tool_preserves_structured_retriever_metadata_with_llm_text() -> None:
    tool = InsuranceRAGTool(RetrievalService(QueryRetriever()))

    text, result = tool.run_with_result("waiting-period")

    assert "waiting-period.pdf" in text
    assert result is not None
    assert result.documents[0].raw_metadata == {
        "source": "waiting-period.pdf",
        "page": 1,
    }


def test_graph_keeps_ordered_sources_from_repeated_rag_calls() -> None:
    tool = InsuranceRAGTool(RetrievalService(QueryRetriever()))
    llm = SequenceLLM([
        _rag_call("first", "rag-1"),
        _rag_call("second", "rag-2"),
        AIMessage(content="final answer"),
    ])
    graph = AgentGraphBuilder(
        tools=[tool],
        llm=llm,
        system_prompt="offline",
        max_context_rounds=5,
    )

    result = graph.invoke(
        user_message="question",
        session_id="rag-sources",
        execution_id="request-1",
    )

    assert [item["source_name"] for item in result["retrieved_docs"]] == [
        "first.pdf",
        "second.pdf",
    ]
    assert [item["source_page"] for item in result["retrieved_docs"]] == [2, 2]
    scores = [item["similarity_score"] for item in result["retrieved_docs"]]
    assert all(type(score) is float for score in scores)
    assert scores == pytest.approx([0.88, 0.88])


def test_concurrent_graph_requests_do_not_share_retrieved_sources() -> None:
    tool = InsuranceRAGTool(RetrievalService(QueryRetriever()))
    graph = AgentGraphBuilder(
        tools=[tool],
        llm=ConcurrentLLM(),
        system_prompt="offline",
        max_context_rounds=5,
    )

    def invoke(query: str) -> dict[str, Any]:
        return graph.invoke(
            user_message=query,
            session_id="shared-session",
            execution_id=f"request-{query}",
        )

    with ThreadPoolExecutor(max_workers=2) as executor:
        first, second = executor.map(invoke, ["alpha", "beta"])

    assert [item["source_name"] for item in first["retrieved_docs"]] == ["alpha.pdf"]
    assert [item["source_name"] for item in second["retrieved_docs"]] == ["beta.pdf"]
