from pathlib import Path
from typing import Any
from uuid import uuid4

from fastapi.testclient import TestClient
from langchain_core.messages import AIMessage
import pytest

import application.bootstrap as bootstrap
from api.main import create_app
from config import PDF_CONFIG, VECTOR_STORE_CONFIG
from graph.graph_builder import AgentGraphBuilder
from rag.langchain.index_builder import LangChainIndexBuilder
from rag.langchain.loader import PDFLoader
from rag.langchain.retriever import LangChainRetriever
from rag.langchain.vector_store import VectorStoreManager


TRACE_ID = "RAG-DUAL-ENGINE-INTEGRATION"
HEADERS = {"X-Trace-Id": TRACE_ID}
QUERY = "What is the waiting period for the controlled Aurora insurance policy?"
DOCUMENT_NAME = "controlled-insurance-terms.txt"
DOCUMENT_TEXT = (
    "Aurora insurance validation document. The controlled policy has an exact "
    "waiting period of 180 days before covered illness benefits begin. This "
    "sentence exists only in the isolated dual-engine validation fixture."
)


class SequenceLLM:
    model_name = "offline-dual-engine-validation-llm"

    def __init__(self) -> None:
        self._responses = [
            AIMessage(
                content="",
                tool_calls=[{
                    "name": "insurance_rag_search",
                    "args": {"query": QUERY},
                    "id": "controlled-rag-call",
                    "type": "tool_call",
                }],
            ),
            AIMessage(content="The controlled waiting period is 180 days."),
        ]

    def bind_tools(self, tools: list[Any]) -> "SequenceLLM":
        return self

    def invoke(self, messages: list[Any]) -> AIMessage:
        return self._responses.pop(0)


def _build_controlled_index(
    rag_engine: str,
    document_dir: Path,
) -> Any:
    if rag_engine == "langchain":
        vector_store = VectorStoreManager()
        builder = LangChainIndexBuilder(
            vector_store=vector_store,
            loader=PDFLoader(pdf_dir=str(document_dir)),
        )
    else:
        from rag.llamaindex.index_builder import LlamaIndexBuilder

        builder = LlamaIndexBuilder()

    build_result = builder.build()
    assert build_result["success"] is True, build_result["message"]
    assert builder.get_stats()["index_loaded"] is True
    return builder


@pytest.mark.parametrize("rag_engine", ["langchain", "llamaindex"])
def test_real_bge_faiss_engine_reaches_formal_agent_sources(
    tmp_path: Path,
    monkeypatch: pytest.MonkeyPatch,
    rag_engine: str,
) -> None:
    document_dir = tmp_path / "documents"
    vector_root = tmp_path / "vectorstore"
    document_dir.mkdir()
    (document_dir / DOCUMENT_NAME).write_text(DOCUMENT_TEXT, encoding="utf-8")
    monkeypatch.setitem(PDF_CONFIG, "pdf_dir", str(document_dir))
    monkeypatch.setitem(VECTOR_STORE_CONFIG, "index_path", str(vector_root))
    monkeypatch.setenv("RAG_ENGINE", rag_engine)
    monkeypatch.setattr(
        bootstrap,
        "AgentGraphBuilder",
        lambda tools: AgentGraphBuilder(
            tools=tools,
            llm=SequenceLLM(),
            system_prompt="Use the RAG tool once, then answer from its result.",
            max_context_rounds=5,
        ),
    )

    _build_controlled_index(rag_engine, document_dir)

    payload = {
        "requestId": str(uuid4()),
        "sessionId": str(uuid4()),
        "message": QUERY,
        "history": [],
    }
    app = create_app()
    with TestClient(app) as client:
        runtime = app.state.runtime
        builder = runtime.knowledge_facade._knowledge_service._builder
        rag_tool = runtime.agent_facade._graph_builder._tools_by_name[
            "insurance_rag_search"
        ]
        retriever = rag_tool._service._retriever
        direct_result = retriever.retrieve(QUERY, top_k=1)
        readiness = client.get(
            "/internal/v1/health/ready",
            headers=HEADERS,
        )
        status = client.get(
            "/internal/v1/knowledge/status",
            headers=HEADERS,
        )
        chat = client.post(
            "/internal/v1/agent/chat",
            headers=HEADERS,
            json=payload,
        )

    assert runtime.rag_engine == rag_engine
    assert direct_result.engine == rag_engine
    assert direct_result.total_found == 1
    assert direct_result.documents[0].source_name == DOCUMENT_NAME
    assert "180 days" in direct_result.documents[0].content
    assert 0.0 <= direct_result.documents[0].similarity_score <= 1.0
    assert builder.get_stats()["rag_engine"] == rag_engine
    assert builder.get_stats()["pdf_count"] == 1
    assert readiness.status_code == 200
    assert readiness.json()["data"] == {
        "status": "UP",
        "ragEngine": rag_engine,
    }
    assert status.status_code == 200
    assert status.json()["data"]["ragEngine"] == rag_engine
    assert status.json()["data"]["indexLoaded"] is True
    assert chat.status_code == 200
    assert chat.json()["data"]["answer"] == (
        "The controlled waiting period is 180 days."
    )
    assert chat.json()["data"]["sources"]
    assert chat.json()["data"]["sources"][0]["documentName"] == DOCUMENT_NAME
    assert "180 days" in chat.json()["data"]["sources"][0]["snippet"]
    assert 0.0 <= chat.json()["data"]["sources"][0]["score"] <= 1.0

    if rag_engine == "langchain":
        assert isinstance(builder, LangChainIndexBuilder)
        assert isinstance(retriever, LangChainRetriever)
        assert (vector_root / "LANGCHAIN_CURRENT").is_file()
        assert (vector_root / "langchain-generations").is_dir()
        assert not (vector_root / "llamaindex" / "CURRENT").exists()
    else:
        from rag.llamaindex.index_builder import LlamaIndexBuilder
        from rag.llamaindex.retriever import LlamaIndexRetriever

        assert isinstance(builder, LlamaIndexBuilder)
        assert isinstance(retriever, LlamaIndexRetriever)
        assert (vector_root / "llamaindex" / "CURRENT").is_file()
        assert (vector_root / "llamaindex" / "generations").is_dir()
        assert not (vector_root / "LANGCHAIN_CURRENT").exists()
