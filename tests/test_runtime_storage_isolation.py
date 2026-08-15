import json
import os
from pathlib import Path
import subprocess
import sys

import pytest


def test_runtime_storage_environment_is_applied_at_process_start(
    tmp_path: Path,
) -> None:
    document_root = tmp_path / "documents"
    vector_root = tmp_path / "vectors"
    environment = os.environ.copy()
    environment.update(
        {
            "DOCUMENT_STORAGE_ROOT": str(document_root),
            "VECTOR_STORE_ROOT": str(vector_root),
            "RAG_ENGINE": "langchain",
            "PYTHONUTF8": "1",
        }
    )
    command = (
        "import json; "
        "from config import PDF_CONFIG, VECTOR_STORE_CONFIG; "
        "print(json.dumps({"
        "'documents': PDF_CONFIG['pdf_dir'], "
        "'vectors': VECTOR_STORE_CONFIG['index_path']"
        "}))"
    )

    completed = subprocess.run(
        [sys.executable, "-c", command],
        cwd=Path(__file__).resolve().parents[1],
        env=environment,
        check=True,
        capture_output=True,
        text=True,
        encoding="utf-8",
    )
    configured = json.loads(completed.stdout)

    assert Path(configured["documents"]) == document_root.resolve()
    assert Path(configured["vectors"]) == vector_root.resolve()


def test_llamaindex_embedding_batches_cpu_work_in_groups_of_32(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    pytest.importorskip("llama_index.core")
    from rag.llamaindex.embedding import LlamaIndexEmbeddingAdapter

    class RecordingEmbeddingManager:
        def __init__(self) -> None:
            self.batches: list[list[str]] = []

        def embed_documents(self, texts: list[str]) -> list[list[float]]:
            self.batches.append(texts)
            return [[float(len(text))] for text in texts]

    manager = RecordingEmbeddingManager()
    monkeypatch.setattr(
        LlamaIndexEmbeddingAdapter,
        "_embedding_manager",
        manager,
    )
    adapter = LlamaIndexEmbeddingAdapter()

    result = adapter.get_text_embedding_batch([str(index) for index in range(65)])

    assert adapter.embed_batch_size == 32
    assert [len(batch) for batch in manager.batches] == [32, 32, 1]
    assert len(result) == 65


def test_llamaindex_builder_reads_controlled_hidden_runtime_directory(
    tmp_path: Path,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    pytest.importorskip("llama_index.core")
    import pymupdf
    import rag.llamaindex.index_builder as builder_module

    document_root = tmp_path / ".runtime-documents"
    document_root.mkdir()
    pdf_path = document_root / "controlled.pdf"
    pdf = pymupdf.open()
    first_page = pdf.new_page()
    first_page.insert_text((72, 72), "controlled insurance terms page one")
    second_page = pdf.new_page()
    second_page.insert_text((72, 72), "controlled waiting period page two")
    pdf.save(pdf_path)
    pdf.close()
    monkeypatch.setitem(
        builder_module.PDF_CONFIG,
        "pdf_dir",
        str(document_root),
    )
    builder = builder_module.LlamaIndexBuilder.__new__(
        builder_module.LlamaIndexBuilder
    )
    captured: list[object] = []
    monkeypatch.setattr(
        builder,
        "_build_index",
        lambda documents: captured.extend(documents),
    )

    result = builder.build()

    assert result["success"] is True
    assert len(captured) == 2
    assert captured[0].text == "controlled insurance terms page one"
    assert captured[0].metadata["file_name"] == "controlled.pdf"
    assert captured[0].metadata["page"] == 1
    assert captured[1].metadata["page"] == 2
    assert "xref" not in "".join(document.text for document in captured)
