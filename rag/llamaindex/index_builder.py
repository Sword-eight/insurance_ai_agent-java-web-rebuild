"""
Insurance AI Agent - LlamaIndex 索引构建器
使用 LlamaIndex 管理 FAISS 索引的完整生命周期。
实现 BaseIndexBuilder 接口。

注意：
  - 不使用 QueryEngine，不调用 LLM。
  - 仅负责索引的生命周期管理。
  - Embedding 复用项目统一的 SentenceTransformer。
"""

import os
import shutil
from pathlib import Path
from threading import RLock
from typing import List, Dict, Any, Optional
from uuid import uuid4

import faiss
from llama_index.core import (
    VectorStoreIndex,
    StorageContext,
    Settings,
    SimpleDirectoryReader,
    Document,
)
from llama_index.core.node_parser import SentenceSplitter
from llama_index.vector_stores.faiss import FaissVectorStore

from rag.base_index_builder import BaseIndexBuilder
from config import PDF_CONFIG, VECTOR_STORE_CONFIG
from rag.llamaindex.embedding import LlamaIndexEmbeddingAdapter
from utils.logger import get_logger
from utils.helpers import Timer

logger = get_logger("rag.llamaindex.builder")


class LlamaIndexBuilder(BaseIndexBuilder):
    """
    LlamaIndex 索引构建器。

    使用 LlamaIndex 框架管理 FAISS 索引的完整生命周期：
    构建 → 保存 → 加载 → 删除。

    注意：LlamaIndex 使用独立的索引目录，与 LangChain 索引隔离。
    """

    def __init__(self) -> None:
        """初始化组件。"""
        self._index_path: Path = Path(VECTOR_STORE_CONFIG["index_path"]) / "llamaindex"
        self._index_path.mkdir(parents=True, exist_ok=True)
        self._generation_root = self._index_path / "generations"
        self._generation_root.mkdir(parents=True, exist_ok=True)
        self._current_file = self._index_path / "CURRENT"

        self._embedding = LlamaIndexEmbeddingAdapter()
        self._index: Optional[VectorStoreIndex] = None
        self._index_lock = RLock()

    # ------------------------------------------------------------------
    # 公共属性
    # ------------------------------------------------------------------

    @property
    def index(self) -> Optional[VectorStoreIndex]:
        """获取当前加载的索引实例。"""
        with self._index_lock:
            return self._index

    @property
    def is_loaded(self) -> bool:
        """索引是否已加载到内存。"""
        with self._index_lock:
            return self._index is not None

    # ------------------------------------------------------------------
    # BaseIndexBuilder 接口 — build / load / delete
    # ------------------------------------------------------------------

    def build(self) -> Dict[str, Any]:
        """
        构建索引：自动扫描文档目录 → 加载 → 切分 → 向量化 → 保存。

        Returns:
            构建结果
        """
        result: Dict[str, Any] = {
            "success": False,
            "pdf_count": 0,
            "chunk_count": 0,
            "vector_count": 0,
            "message": "",
        }

        try:
            pdf_dir: Path = Path(PDF_CONFIG["pdf_dir"])
            doc_files: list = list(pdf_dir.glob("*.*"))
            doc_files = [f for f in doc_files if f.suffix.lower() in (".pdf", ".txt")]
            result["pdf_count"] = len(doc_files)

            if not doc_files:
                result["message"] = "data/pdf/ 目录中没有文档文件，请先上传文档"
                logger.warning(result["message"])
                return result

            with Timer("LlamaIndex 文档加载"):
                documents = SimpleDirectoryReader(
                    input_dir=str(pdf_dir),
                    recursive=False,
                ).load_data()

            if not documents:
                result["message"] = "文档加载失败或文档内容为空"
                return result

            with Timer("LlamaIndex 索引构建"):
                self._build_index(documents=documents)

            result.update({
                "success": True,
                "chunk_count": len(documents),
                "message": f"LlamaIndex 知识库构建成功: {len(doc_files)} 个文档",
            })
            logger.info(result["message"])

        except Exception as e:
            result["message"] = f"LlamaIndex 知识库构建失败: {e}"
            logger.error(result["message"], exc_info=True)

        return result

    def load(self) -> bool:
        """从磁盘加载索引。"""
        if not self.index_exists():
            logger.warning("本地索引文件不存在，无法加载")
            return False

        try:
            from llama_index.core import load_index_from_storage

            Settings.embed_model = self._embedding
            Settings.llm = None

            active_path = self._active_index_path()
            faiss_store = FaissVectorStore.from_persist_dir(str(active_path))
            storage_context = StorageContext.from_defaults(
                vector_store=faiss_store,
                persist_dir=str(active_path),
            )

            loaded = load_index_from_storage(
                storage_context=storage_context,
                embed_model=self._embedding,
            )
            with self._index_lock:
                self._index = loaded

            logger.info("LlamaIndex 索引加载成功")
            return True

        except Exception as e:
            logger.error(f"加载 LlamaIndex 索引失败: {e}", exc_info=True)
            return False

    def delete(self) -> None:
        """删除索引目录。"""
        with self._index_lock:
            existed = self.index_exists()
            self._index = None
            self._current_file.unlink(missing_ok=True)
            if self._generation_root.exists():
                shutil.rmtree(self._generation_root)
            self._generation_root.mkdir(parents=True, exist_ok=True)
            for name in ("docstore.json", "index_store.json", "faiss.index"):
                (self._index_path / name).unlink(missing_ok=True)
        logger.info(
            f"LlamaIndex 索引已删除: {self._index_path}"
            if existed else "LlamaIndex 索引目录不存在，无需删除"
        )

    # ------------------------------------------------------------------
    # BaseIndexBuilder 接口 — 查询
    # ------------------------------------------------------------------

    def get_stats(self) -> Dict[str, Any]:
        """获取索引统计信息。"""
        pdf_dir: Path = Path(PDF_CONFIG["pdf_dir"])
        doc_files: list = list(pdf_dir.glob("*.*")) if pdf_dir.exists() else []
        doc_files = [f for f in doc_files if f.suffix.lower() in (".pdf", ".txt")]

        loaded_index = self.index
        vector_store = (
            loaded_index.storage_context.vector_store
            if loaded_index is not None else None
        )
        faiss_index = getattr(vector_store, "_faiss_index", None)
        return {
            "index_loaded": self.is_loaded,
            "index_exists": self.index_exists(),
            "index_path": str(self._index_path),
            "embedding_model": "BAAI/bge-base-zh-v1.5 (via SentenceTransformer)",
            "total_vectors": int(faiss_index.ntotal) if faiss_index is not None else 0,
            "pdf_count": len(doc_files),
            "chunk_size": PDF_CONFIG["chunk_size"],
            "chunk_overlap": PDF_CONFIG["chunk_overlap"],
            "rag_engine": "llamaindex",
        }

    def index_exists(self) -> bool:
        """检查本地是否存在已保存的索引。"""
        return (self._active_index_path() / "docstore.json").is_file()

    def list_documents(self) -> List[Dict[str, Any]]:
        """列出文档目录中的文件。"""
        pdf_dir: Path = Path(PDF_CONFIG["pdf_dir"])
        if not pdf_dir.exists():
            return []

        files = sorted(
            [f for f in pdf_dir.glob("*.*")
             if f.suffix.lower() in (".pdf", ".txt")]
        )
        return [
            {
                "name": f.name,
                "size": f.stat().st_size,
                "path": str(f),
            }
            for f in files
        ]

    # ------------------------------------------------------------------
    # 内部方法（保留向后兼容）
    # ------------------------------------------------------------------

    def build_index(
        self,
        documents: Optional[List[Document]] = None,
        nodes: Optional[List[Any]] = None,
    ) -> None:
        """
        构建 FAISS 索引并持久化（内部方法，也可从外部直接调用用于测试）。

        Args:
            documents: LlamaIndex Document 列表
            nodes:     预处理好的 Node 列表（与 documents 二选一）
        """
        self._build_index(documents=documents, nodes=nodes)

    def _build_index(
        self,
        documents: Optional[List[Document]] = None,
        nodes: Optional[List[Any]] = None,
    ) -> None:
        """内部索引构建逻辑。"""
        Settings.embed_model = self._embedding
        Settings.llm = None

        faiss_dim: int = len(self._embedding._get_query_embedding("dim_test"))
        faiss_index = faiss.IndexFlatL2(faiss_dim)
        faiss_store = FaissVectorStore(faiss_index=faiss_index)

        storage_context = StorageContext.from_defaults(vector_store=faiss_store)

        if documents:
            node_parser = SentenceSplitter(
                chunk_size=PDF_CONFIG["chunk_size"],
                chunk_overlap=PDF_CONFIG["chunk_overlap"],
            )
            logger.info(
                f"开始构建 LlamaIndex 索引: {len(documents)} 个文档, "
                f"chunk_size={PDF_CONFIG['chunk_size']}, "
                f"chunk_overlap={PDF_CONFIG['chunk_overlap']}"
            )
            candidate = VectorStoreIndex.from_documents(
                documents=documents,
                storage_context=storage_context,
                embed_model=self._embedding,
                transformations=[node_parser],
                show_progress=False,
            )
        elif nodes:
            logger.info(f"开始构建 LlamaIndex 索引: {len(nodes)} 个 Node")
            candidate = VectorStoreIndex(
                nodes=nodes,
                storage_context=storage_context,
                embed_model=self._embedding,
                show_progress=False,
            )
        else:
            logger.warning("documents 和 nodes 均为空，无法构建索引")
            return

        self._save_index(candidate)
        logger.info(f"LlamaIndex 索引构建完成，已保存到 {self._index_path}")

    def _save_index(self, candidate: VectorStoreIndex) -> None:
        """将索引持久化到磁盘。"""
        generation_name = uuid4().hex
        generation = self._generation_root / generation_name
        marker = self._index_path / f".CURRENT-{generation_name}.tmp"
        try:
            generation.mkdir(parents=False, exist_ok=False)
            candidate.storage_context.persist(persist_dir=str(generation))
            vector_store = candidate.storage_context.vector_store
            if hasattr(vector_store, '_faiss_index') and vector_store._faiss_index is not None:
                faiss.write_index(
                    vector_store._faiss_index, str(generation / "faiss.index")
                )
            if not (generation / "docstore.json").is_file():
                raise RuntimeError("candidate LlamaIndex is incomplete")
            marker.write_text(generation_name, encoding="ascii")
            with self._index_lock:
                os.replace(marker, self._current_file)
                self._index = candidate
                for child in self._generation_root.iterdir():
                    if child.name != generation_name and child.is_dir():
                        shutil.rmtree(child, ignore_errors=True)
            logger.info(f"LlamaIndex 索引已原子发布: generation={generation_name}")
        except Exception:
            marker.unlink(missing_ok=True)
            if generation.exists():
                shutil.rmtree(generation, ignore_errors=True)
            raise

    def _active_index_path(self) -> Path:
        with self._index_lock:
            if self._current_file.is_file():
                try:
                    name = self._current_file.read_text(encoding="ascii").strip()
                    if name and name.isalnum():
                        candidate = (self._generation_root / name).resolve()
                        if candidate.parent == self._generation_root.resolve():
                            return candidate
                except OSError:
                    pass
            return self._index_path
