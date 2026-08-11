from pathlib import Path
from threading import RLock

import pytest

from rag.langchain.vector_store import VectorStoreManager


class FakeCandidate:
    def __init__(self, fail: bool = False) -> None:
        self.fail = fail

    def save_local(self, folder: str) -> None:
        path = Path(folder)
        (path / "index.faiss").write_bytes(b"candidate-index")
        if self.fail:
            raise RuntimeError("candidate failed")
        (path / "index.pkl").write_bytes(b"candidate-metadata")


def manager(tmp_path: Path) -> VectorStoreManager:
    instance = VectorStoreManager.__new__(VectorStoreManager)
    instance.index_path = tmp_path
    instance._generation_root = tmp_path / "langchain-generations"
    instance._generation_root.mkdir()
    instance._current_file = tmp_path / "LANGCHAIN_CURRENT"
    instance._vector_store = "old"
    instance._index_lock = RLock()
    return instance


def test_candidate_generation_is_published_through_atomic_marker(tmp_path: Path) -> None:
    store = manager(tmp_path)
    candidate = FakeCandidate()

    store._publish(candidate)

    active = store._active_folder_unlocked()
    assert active.parent == store._generation_root
    assert (active / "index.faiss").read_bytes() == b"candidate-index"
    assert (active / "index.pkl").read_bytes() == b"candidate-metadata"
    assert store._vector_store is candidate


def test_failed_candidate_never_replaces_loaded_index(tmp_path: Path) -> None:
    store = manager(tmp_path)

    with pytest.raises(RuntimeError, match="candidate failed"):
        store._publish(FakeCandidate(fail=True))

    assert store._vector_store == "old"
    assert not store._current_file.exists()
    assert list(store._generation_root.iterdir()) == []
