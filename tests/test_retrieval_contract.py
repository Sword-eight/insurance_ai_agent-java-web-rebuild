"""BaseRetriever 及统一返回结构的离线契约测试。"""

from typing import Any

import pytest

from rag.base_retriever import (
    BaseRetriever,
    RetrievalDocument,
    RetrievalResult,
)


class InMemoryRetriever(BaseRetriever):
    """只返回内存数据的最小 Retriever，不加载任何索引或模型。"""

    def retrieve(
        self,
        query: str,
        top_k: int = 5,
        score_threshold: float = 0.0,
    ) -> RetrievalResult:
        metadata: dict[str, Any] = {
            "top_k": top_k,
            "score_threshold": score_threshold,
        }
        document = RetrievalDocument(
            content="等待期为 90 天",
            source_name="fixture.txt",
            source_page=1,
            similarity_score=0.9,
            engine="memory",
            raw_metadata=metadata,
        )
        return RetrievalResult(
            query=query,
            documents=[document],
            total_found=1,
            retrieval_time_ms=0.1,
            engine="memory",
            applied_filters={"score_threshold": score_threshold},
        )


def test_base_retriever_cannot_be_instantiated_directly() -> None:
    with pytest.raises(TypeError):
        BaseRetriever()


def test_retriever_returns_normalized_result_contract() -> None:
    retriever = InMemoryRetriever()

    result = retriever.retrieve("等待期", top_k=3, score_threshold=0.5)

    assert isinstance(retriever, BaseRetriever)
    assert isinstance(result, RetrievalResult)
    assert result.query == "等待期"
    assert result.engine == "memory"
    assert result.total_found == 1
    assert result.applied_filters == {"score_threshold": 0.5}
    assert len(result.documents) == 1

    document = result.documents[0]
    assert isinstance(document, RetrievalDocument)
    assert document.content == "等待期为 90 天"
    assert document.source_name == "fixture.txt"
    assert document.source_page == 1
    assert document.similarity_score == 0.9
    assert document.engine == result.engine
    assert document.raw_metadata == {
        "top_k": 3,
        "score_threshold": 0.5,
    }
