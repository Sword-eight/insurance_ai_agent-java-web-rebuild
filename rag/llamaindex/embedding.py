"""LlamaIndex adapter for the process-shared EmbeddingManager."""

from typing import Any, List

from llama_index.core.embeddings import BaseEmbedding

from rag.embedding import EmbeddingManager


class LlamaIndexEmbeddingAdapter(BaseEmbedding):
    _embedding_manager: EmbeddingManager = None  # type: ignore[assignment]

    def __init__(self, **kwargs: Any) -> None:
        kwargs.setdefault("embed_batch_size", 32)
        super().__init__(**kwargs)
        if self._embedding_manager is None:
            LlamaIndexEmbeddingAdapter._embedding_manager = EmbeddingManager()

    @classmethod
    def class_name(cls) -> str:
        return "SentenceTransformer_Adapter"

    def _get_query_embedding(self, query: str) -> List[float]:
        return self._embedding_manager.embed_query(query)

    def _get_text_embedding(self, text: str) -> List[float]:
        return self._embedding_manager.embed_query(text)

    def _get_text_embeddings(self, texts: List[str]) -> List[List[float]]:
        return self._embedding_manager.embed_documents(texts)

    async def _aget_query_embedding(self, query: str) -> List[float]:
        return self._get_query_embedding(query)

    async def _aget_text_embedding(self, text: str) -> List[float]:
        return self._get_text_embedding(text)
