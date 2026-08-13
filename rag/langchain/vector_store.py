"""LangChain FAISS vector store with failure-safe generation publishing."""

import os
from pathlib import Path
import shutil
from threading import RLock
from typing import Any, Dict, List, Optional
from uuid import uuid4

from langchain_community.vectorstores import FAISS
from langchain_core.documents import Document as LCDocument

from config import VECTOR_STORE_CONFIG
from rag.embedding import EmbeddingManager
from utils.logger import get_logger


logger = get_logger("rag.langchain.vector_store")


class VectorStoreManager:
    """Build, publish, load and query one process-shared FAISS index."""

    def __init__(self) -> None:
        self.index_path = Path(VECTOR_STORE_CONFIG["index_path"])
        self.index_path.mkdir(parents=True, exist_ok=True)
        self._generation_root = self.index_path / "langchain-generations"
        self._generation_root.mkdir(parents=True, exist_ok=True)
        self._current_file = self.index_path / "LANGCHAIN_CURRENT"
        self._embedding_manager = EmbeddingManager()
        self._vector_store: Optional[FAISS] = None
        self._index_lock = RLock()

    @property
    def is_loaded(self) -> bool:
        return self._vector_store is not None

    @property
    def embedding_model_name(self) -> str:
        return self._embedding_manager.model_name

    def index_exists(self) -> bool:
        with self._index_lock:
            folder = self._active_folder_unlocked()
            return self._folder_complete(folder)

    def build_index(self, chunks: List[Dict[str, Any]]) -> None:
        if not chunks:
            logger.warning("Chunk 列表为空，无法构建索引")
            return

        logger.info(f"开始构建 FAISS 候选索引，Chunk 数量: {len(chunks)}")
        documents = [
            LCDocument(page_content=item["page_content"], metadata=item["metadata"])
            for item in chunks
        ]
        candidate = FAISS.from_documents(
            documents=documents,
            embedding=self._embedding_manager.model,
        )
        self._publish(candidate)

    def _save_index(self) -> None:
        if self._vector_store is None:
            logger.warning("索引为空，无法保存")
            return
        self._publish(self._vector_store)

    def _publish(self, candidate: FAISS) -> None:
        generation_name = uuid4().hex
        generation = self._generation_root / generation_name
        marker = self.index_path / f".LANGCHAIN_CURRENT-{generation_name}.tmp"
        try:
            generation.mkdir(parents=False, exist_ok=False)
            candidate.save_local(str(generation))
            if not self._folder_complete(generation):
                raise RuntimeError("candidate index is incomplete")
            marker.write_text(generation_name, encoding="ascii")
            with self._index_lock:
                os.replace(marker, self._current_file)
                self._vector_store = candidate
                self._cleanup_generations_unlocked(generation_name)
            logger.info(f"FAISS 索引已原子发布: generation={generation_name}")
        except Exception:
            marker.unlink(missing_ok=True)
            if generation.exists():
                shutil.rmtree(generation, ignore_errors=True)
            raise

    def load_index(self) -> bool:
        with self._index_lock:
            folder = self._active_folder_unlocked()
            if not self._folder_complete(folder):
                logger.warning("本地索引文件不存在，无法加载")
                return False
            try:
                loaded = FAISS.load_local(
                    folder_path=str(folder),
                    embeddings=self._embedding_manager.model,
                    allow_dangerous_deserialization=True,
                )
                self._vector_store = loaded
                logger.info(f"FAISS 索引加载成功，共 {loaded.index.ntotal} 条向量")
                return True
            except Exception as exc:
                logger.error(f"加载 FAISS 索引失败: {exc}")
                return False

    def similarity_search(
        self,
        query: str,
        top_k: int | None = None,
        score_threshold: float = 0.0,
    ) -> List[Dict[str, Any]]:
        with self._index_lock:
            if self._vector_store is None and not self.load_index():
                logger.error("索引未加载，无法执行检索")
                return []
            assert self._vector_store is not None
            results = self._vector_store.similarity_search_with_score(
                query, k=top_k or VECTOR_STORE_CONFIG["top_k"]
            )

        formatted: List[Dict[str, Any]] = []
        for document, score in results:
            similarity = round(1.0 / (1.0 + score), 4)
            if similarity >= score_threshold:
                formatted.append(
                    {
                        "page_content": document.page_content,
                        "metadata": document.metadata,
                        "similarity_score": similarity,
                    }
                )
        logger.info(f"检索完成: top_k={top_k}, 返回 {len(formatted)} 条结果")
        return formatted

    def get_index_stats(self) -> Dict[str, Any]:
        with self._index_lock:
            folder = self._active_folder_unlocked()
            return {
                "index_loaded": self.is_loaded,
                "index_exists": self._folder_complete(folder),
                "index_path": str(self.index_path),
                "embedding_model": self.embedding_model_name,
                "total_vectors": (
                    self._vector_store.index.ntotal
                    if self._vector_store is not None else 0
                ),
            }

    def delete_index(self) -> None:
        with self._index_lock:
            deleted = self._folder_complete(self._active_folder_unlocked())
            self._current_file.unlink(missing_ok=True)
            (self.index_path / "index.faiss").unlink(missing_ok=True)
            (self.index_path / "index.pkl").unlink(missing_ok=True)
            if self._generation_root.exists():
                shutil.rmtree(self._generation_root)
            self._generation_root.mkdir(parents=True, exist_ok=True)
            self._vector_store = None
        logger.info("FAISS 索引已删除" if deleted else "索引文件不存在，无需删除")

    @staticmethod
    def _folder_complete(folder: Path) -> bool:
        return (folder / "index.faiss").is_file() and (folder / "index.pkl").is_file()

    def _active_folder_unlocked(self) -> Path:
        if self._current_file.is_file():
            try:
                name = self._current_file.read_text(encoding="ascii").strip()
                if name and name.isalnum():
                    candidate = (self._generation_root / name).resolve()
                    if candidate.parent == self._generation_root.resolve():
                        return candidate
            except OSError:
                pass
        return self.index_path

    def _cleanup_generations_unlocked(self, active_name: str) -> None:
        for child in self._generation_root.iterdir():
            if child.name != active_name and child.is_dir():
                shutil.rmtree(child, ignore_errors=True)
