import builtins

from fastapi.testclient import TestClient
import pytest

import application.bootstrap as bootstrap
from api.main import _init_api_runtime, create_app
from application.rag_engine import (
    RagEngineConfigurationError,
    RagEngineDependencyError,
)
from application.runtime import ApplicationRuntime
from config import get_rag_engine, normalize_rag_engine


TRACE_ID = "RAG-ENGINE-RUNTIME-TEST"
HEADERS = {"X-Trace-Id": TRACE_ID}


@pytest.mark.parametrize(
    ("configured", "expected"),
    [
        (None, "langchain"),
        ("langchain", "langchain"),
        (" LLAMAINDEX ", "llamaindex"),
    ],
)
def test_rag_engine_configuration_is_normalized_and_strict(
    monkeypatch: pytest.MonkeyPatch,
    configured: str | None,
    expected: str,
) -> None:
    if configured is None:
        monkeypatch.delenv("RAG_ENGINE", raising=False)
    else:
        monkeypatch.setenv("RAG_ENGINE", configured)

    assert get_rag_engine() == expected


def test_invalid_rag_engine_does_not_fallback() -> None:
    with pytest.raises(
        RagEngineConfigurationError,
        match=(
            r"Unsupported RAG_ENGINE: unknown\. "
            r"Expected langchain or llamaindex\."
        ),
    ):
        normalize_rag_engine("unknown")


def test_invalid_environment_aborts_formal_fastapi_startup(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.setenv("RAG_ENGINE", "unknown")

    with pytest.raises(
        RagEngineConfigurationError,
        match=r"Unsupported RAG_ENGINE: unknown",
    ):
        with TestClient(create_app()):
            pass


def test_missing_llamaindex_dependency_has_actionable_error(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    original_import = builtins.__import__

    def guarded_import(name, globals=None, locals=None, fromlist=(), level=0):
        if name.startswith("rag.llamaindex"):
            raise ModuleNotFoundError(
                "No module named 'llama_index'",
                name="llama_index",
            )
        return original_import(name, globals, locals, fromlist, level)

    monkeypatch.setattr(builtins, "__import__", guarded_import)

    with pytest.raises(
        RagEngineDependencyError,
        match=r"requirements-llamaindex\.txt",
    ):
        bootstrap._rag_component_types("llamaindex")


def test_formal_fastapi_factory_passes_environment_engine(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    captured: dict[str, str] = {}
    expected_runtime = ApplicationRuntime(
        agent_facade=object(),
        knowledge_facade=object(),
        rag_engine="llamaindex",
    )

    def fake_init_api_runtime(rag_engine: str):
        captured["rag_engine"] = rag_engine
        return expected_runtime

    monkeypatch.setenv("RAG_ENGINE", " LlamaIndex ")
    monkeypatch.setattr(bootstrap, "init_api_runtime", fake_init_api_runtime)

    assert _init_api_runtime() is expected_runtime
    assert captured == {"rag_engine": "llamaindex"}


def test_rag_configuration_errors_abort_lifespan_startup() -> None:
    def failing_factory() -> ApplicationRuntime:
        raise RagEngineDependencyError("install requirements-llamaindex.txt")

    app = create_app(runtime_factory=failing_factory)
    with pytest.raises(
        RagEngineDependencyError,
        match="requirements-llamaindex.txt",
    ):
        with TestClient(app):
            pass


@pytest.mark.parametrize("rag_engine", ["langchain", "llamaindex"])
def test_readiness_reports_effective_runtime_engine(rag_engine: str) -> None:
    runtime = ApplicationRuntime(
        agent_facade=object(),
        knowledge_facade=object(),
        rag_engine=rag_engine,
    )
    app = create_app(runtime_factory=lambda: runtime)

    with TestClient(app) as client:
        response = client.get("/internal/v1/health/ready", headers=HEADERS)

    assert response.status_code == 200
    assert response.json()["data"] == {
        "status": "UP",
        "ragEngine": rag_engine,
    }


@pytest.mark.parametrize("rag_engine", ["langchain", "llamaindex"])
def test_bootstrap_wires_matching_concrete_object_graph(
    monkeypatch: pytest.MonkeyPatch,
    rag_engine: str,
) -> None:
    if rag_engine == "llamaindex":
        pytest.importorskip("llama_index.core")

    vector_store_type, builder_type, retriever_type = (
        bootstrap._rag_component_types(rag_engine)
    )

    class FakeEmbeddingManager:
        pass

    class FakeGraphBuilder:
        def __init__(self, tools):
            self.tools = tools
            self.graph = object()

    class FakeStateManager:
        def __init__(self, graph):
            self.graph = graph

    import rag.embedding as embedding_module

    monkeypatch.setattr(embedding_module, "EmbeddingManager", FakeEmbeddingManager)
    monkeypatch.setattr(bootstrap, "AgentGraphBuilder", FakeGraphBuilder)
    monkeypatch.setattr(bootstrap, "StateManager", FakeStateManager)
    monkeypatch.setattr(builder_type, "__init__", lambda self, *args, **kwargs: None)
    monkeypatch.setattr(
        builder_type,
        "get_stats",
        lambda self: {
            "index_loaded": False,
            "index_exists": False,
            "rag_engine": rag_engine,
        },
    )
    monkeypatch.setattr(retriever_type, "__init__", lambda self, *args, **kwargs: None)
    if vector_store_type is not None:
        monkeypatch.setattr(vector_store_type, "__init__", lambda self: None)

    services = bootstrap.init_services(
        rag_engine=rag_engine,
        build_missing_index=False,
    )

    assert services["rag_engine"] == rag_engine
    assert isinstance(services["index_builder"], builder_type)
    assert isinstance(services["retriever"], retriever_type)
    assert services["knowledge_service"]._builder is services["index_builder"]
    assert services["retrieval_service"]._retriever is services["retriever"]


def test_llamaindex_retriever_normalizes_native_faiss_score_semantics() -> None:
    pytest.importorskip("llama_index.core")
    from rag.llamaindex.retriever import LlamaIndexRetriever

    class FakeNode:
        score = 1.75
        metadata = {"file_name": "controlled.txt", "page": 3}
        text = "controlled content"
        node_id = "node-1"
        node = object()

    class FakeIndexRetriever:
        def retrieve(self, query: str):
            return [FakeNode()]

    class FakeIndex:
        def as_retriever(self, similarity_top_k: int):
            return FakeIndexRetriever()

    class FakeBuilder:
        is_loaded = True
        index = FakeIndex()

    retriever = LlamaIndexRetriever(builder=FakeBuilder())
    result = retriever.retrieve("controlled")

    assert result.documents[0].similarity_score == 0.125
    assert result.documents[0].source_page == 3
    assert result.documents[0].raw_metadata["native_score"] == 1.75
    assert result.documents[0].raw_metadata["native_score_semantics"] == (
        "squared_l2_distance"
    )
    assert result.documents[0].raw_metadata["score_semantics"] == (
        "cosine_similarity_from_normalized_squared_l2"
    )
    from services.retrieval_service import RetrievalService

    assert "相似度: 12.50%" in RetrievalService(retriever).format_for_llm(result)
