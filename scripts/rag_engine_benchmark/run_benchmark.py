"""Run the frozen LangChain vs LlamaIndex benchmark without changing product code."""

from __future__ import annotations

import argparse
import json
import math
import os
import platform
import statistics
import sys
import time
from datetime import datetime, timezone
from pathlib import Path
from typing import Any


REPOSITORY_ROOT = Path(__file__).resolve().parents[2]
if str(REPOSITORY_ROOT) not in sys.path:
    sys.path.insert(0, str(REPOSITORY_ROOT))
DEFAULT_CASES = REPOSITORY_ROOT / "evidence" / "rag_engine_benchmark" / "cases.json"
DEFAULT_OUTPUT = REPOSITORY_ROOT / "artifacts" / "rag_engine_benchmark"
CORPUS_FILE = REPOSITORY_ROOT / "data" / "pdf" / "中国人寿重大疾病保险条款.txt"
CHUNK_SIZE = 500
CHUNK_OVERLAP = 100
TOP_K = 5
RETRIEVAL_REPEATS = 3


def write_json(path: Path, value: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2), encoding="utf-8")


def percentile(values: list[float], fraction: float) -> float | None:
    if not values:
        return None
    ordered = sorted(values)
    position = (len(ordered) - 1) * fraction
    lower = math.floor(position)
    upper = math.ceil(position)
    if lower == upper:
        return round(ordered[lower], 3)
    value = ordered[lower] + (ordered[upper] - ordered[lower]) * (position - lower)
    return round(value, 3)


def latency_summary(values: list[float]) -> dict[str, float | None]:
    return {
        "count": len(values),
        "mean": round(statistics.fmean(values), 3) if values else None,
        "p50": round(statistics.median(values), 3) if values else None,
        "p95": percentile(values, 0.95),
        "min": round(min(values), 3) if values else None,
        "max": round(max(values), 3) if values else None,
    }


def fixed_chunks(text: str, source_name: str) -> list[dict[str, Any]]:
    """Create one engine-independent, deterministic paragraph-aware chunk set."""
    paragraphs = [
        part.strip()
        for part in text.replace("\r\n", "\n").split("\n\n")
        if part.strip()
    ]
    chunks: list[str] = []
    current = ""
    for paragraph in paragraphs:
        candidate = paragraph if not current else f"{current}\n\n{paragraph}"
        if current and len(candidate) > CHUNK_SIZE:
            chunks.append(current)
            current = f"{current[-CHUNK_OVERLAP:]}\n\n{paragraph}"
            if len(current) > CHUNK_SIZE:
                current = paragraph
        else:
            current = candidate
    if current:
        chunks.append(current)
    return [
        {
            "chunk_id": f"chunk-{index:03d}",
            "document_id": "terms-controlled-v1",
            "document_name": source_name,
            "page": 1,
            "text": chunk,
        }
        for index, chunk in enumerate(chunks, start=1)
    ]


def directory_size(path: Path) -> int:
    return sum(item.stat().st_size for item in path.rglob("*") if item.is_file())


def build_engines(chunks: list[dict[str, Any]], index_root: Path):
    os.environ["VECTOR_STORE_ROOT"] = str(index_root)
    os.environ["DOCUMENT_STORAGE_ROOT"] = str(DEFAULT_OUTPUT / "corpus")

    from langchain_core.documents import Document as LCDocument
    from llama_index.core.schema import TextNode
    from rag.embedding import EmbeddingManager
    from rag.langchain.retriever import LangChainRetriever
    from rag.langchain.vector_store import VectorStoreManager
    from rag.llamaindex.index_builder import LlamaIndexBuilder
    from rag.llamaindex.retriever import LlamaIndexRetriever

    embedding = EmbeddingManager()
    dimension = len(embedding.embed_query("benchmark warmup"))

    langchain_store = VectorStoreManager()
    langchain_payload = [
        {
            "page_content": item["text"],
            "metadata": {
                "source": item["document_name"],
                "page": item["page"],
                "chunk_id": item["chunk_id"],
                "document_id": item["document_id"],
            },
        }
        for item in chunks
    ]
    started = time.perf_counter()
    langchain_store.build_index(langchain_payload)
    langchain_build_ms = (time.perf_counter() - started) * 1000

    llama_builder = LlamaIndexBuilder()
    nodes = []
    for item in chunks:
        metadata = {
            "file_name": item["document_name"],
            "page": item["page"],
            "chunk_id": item["chunk_id"],
            "document_id": item["document_id"],
        }
        nodes.append(TextNode(
            text=item["text"],
            metadata=metadata,
            excluded_embed_metadata_keys=list(metadata),
            excluded_llm_metadata_keys=list(metadata),
        ))
    started = time.perf_counter()
    llama_builder.build_index(nodes=nodes)
    llama_build_ms = (time.perf_counter() - started) * 1000

    started = time.perf_counter()
    reloaded_langchain = VectorStoreManager()
    langchain_loaded = reloaded_langchain.load_index()
    langchain_load_ms = (time.perf_counter() - started) * 1000
    started = time.perf_counter()
    reloaded_llama = LlamaIndexBuilder()
    llama_loaded = reloaded_llama.load()
    llama_load_ms = (time.perf_counter() - started) * 1000
    if not langchain_loaded or not llama_loaded:
        raise RuntimeError("one or both benchmark indices failed to reload")

    build = {
        "common": {
            "documents": 1,
            "chunks": len(chunks),
            "embeddings": len(chunks),
            "embeddingModel": embedding.model_name,
            "embeddingDimension": dimension,
            "device": "cpu",
            "normalizeEmbeddings": True,
            "chunkSize": CHUNK_SIZE,
            "chunkOverlap": CHUNK_OVERLAP,
            "topK": TOP_K,
        },
        "langchain": {
            "totalBuildTimeMs": round(langchain_build_ms, 3),
            "loadTimeMs": round(langchain_load_ms, 3),
            "indexSizeBytes": directory_size(index_root / "langchain-generations"),
            "phaseTimingAvailable": False,
        },
        "llamaindex": {
            "totalBuildTimeMs": round(llama_build_ms, 3),
            "loadTimeMs": round(llama_load_ms, 3),
            "indexSizeBytes": directory_size(index_root / "llamaindex"),
            "phaseTimingAvailable": False,
        },
    }
    retrievers = {
        "langchain": LangChainRetriever(vector_store_manager=reloaded_langchain),
        "llamaindex": LlamaIndexRetriever(builder=reloaded_llama),
    }
    return retrievers, build


def chunk_id(document: Any) -> str | None:
    value = document.raw_metadata.get("chunk_id")
    return str(value) if value else None


def retrieval_benchmark(
    engine: str,
    retriever: Any,
    cases: list[dict[str, Any]],
) -> dict[str, Any]:
    retriever.retrieve("等待期", top_k=TOP_K)
    results = []
    all_latencies: list[float] = []
    for case in cases:
        query = case.get("retrievalQuery", case["question"])
        repeats = []
        first_documents = None
        for _ in range(RETRIEVAL_REPEATS):
            started = time.perf_counter()
            result = retriever.retrieve(query, top_k=TOP_K)
            elapsed_ms = (time.perf_counter() - started) * 1000
            all_latencies.append(elapsed_ms)
            repeats.append(round(elapsed_ms, 3))
            if first_documents is None:
                first_documents = result.documents
        documents = first_documents or []
        results.append({
            "caseId": case["caseId"],
            "query": query,
            "retrievalEvaluated": case.get("retrievalEvaluated", True),
            "expectedDocuments": case["expectedDocuments"],
            "expectedChunks": case["expectedChunks"],
            "latencyMs": repeats,
            "results": [
                {
                    "rank": rank,
                    "chunkId": chunk_id(document),
                    "documentName": document.source_name,
                    "page": document.source_page,
                    "score": float(document.similarity_score),
                }
                for rank, document in enumerate(documents, start=1)
            ],
        })
        print(f"retrieval {engine} {case['caseId']}", flush=True)
    return {
        "engine": engine,
        "warmupQueries": 1,
        "repeatsPerQuery": RETRIEVAL_REPEATS,
        "latencyMs": latency_summary(all_latencies),
        "cases": results,
    }


def retrieval_metrics(payload: dict[str, Any]) -> dict[str, Any]:
    evaluated = [case for case in payload["cases"] if case["retrievalEvaluated"]]
    recall = {1: [], 3: [], 5: []}
    reciprocal_ranks = []
    ndcg5 = []
    document_hits = []
    page_hits = []
    for case in evaluated:
        expected = set(case["expectedChunks"])
        ranked = [item["chunkId"] for item in case["results"]]
        for k in recall:
            recall[k].append(len(expected.intersection(ranked[:k])) / len(expected))
        ranks = [index + 1 for index, value in enumerate(ranked) if value in expected]
        reciprocal_ranks.append(1.0 / min(ranks) if ranks else 0.0)
        gains = [1.0 if value in expected else 0.0 for value in ranked[:5]]
        dcg = sum(gain / math.log2(index + 2) for index, gain in enumerate(gains))
        ideal = sum(1.0 / math.log2(index + 2) for index in range(min(len(expected), 5)))
        ndcg5.append(dcg / ideal if ideal else 0.0)
        expected_docs = set(case["expectedDocuments"])
        document_hits.append(any(item["documentName"] in expected_docs for item in case["results"][:5]))
        page_hits.append(any(item["page"] == 1 for item in case["results"][:5]))
    return {
        "evaluatedCases": len(evaluated),
        "excludedCases": len(payload["cases"]) - len(evaluated),
        "recallAt1": round(statistics.fmean(recall[1]), 4),
        "recallAt3": round(statistics.fmean(recall[3]), 4),
        "recallAt5": round(statistics.fmean(recall[5]), 4),
        "mrr": round(statistics.fmean(reciprocal_ranks), 4),
        "nDCGAt5Binary": round(statistics.fmean(ndcg5), 4),
        "documentHitRateAt5": round(statistics.fmean(document_hits), 4),
        "pageHitRateAt5": round(statistics.fmean(page_hits), 4),
    }


def answer_quality(answer: str, context: str, key: dict[str, Any]) -> dict[str, Any]:
    required_all = key.get("requiredAll", [])
    required_any = key.get("requiredAny", [])
    forbidden = key.get("forbiddenClaims", [])
    matched_all = [item for item in required_all if item in answer]
    matched_any = [item for item in required_any if item in answer]
    forbidden_hits = [item for item in forbidden if item in answer]
    all_ok = len(matched_all) == len(required_all)
    any_ok = not required_any or bool(matched_any)
    if all_ok and any_ok and not forbidden_hits:
        correctness = "Correct"
    elif matched_all or matched_any:
        correctness = "Partial"
    else:
        correctness = "Incorrect"
    support_terms = [item for item in matched_all if item not in context]
    faithful = not support_terms and not forbidden_hits
    hallucinated = bool(forbidden_hits) or (bool(required_any) and not matched_any)
    return {
        "correctness": correctness,
        "matchedRequiredAll": matched_all,
        "matchedRequiredAny": matched_any,
        "forbiddenHits": forbidden_hits,
        "unsupportedMatchedFacts": support_terms,
        "faithfulnessProxy": faithful,
        "hallucinationProxy": hallucinated,
    }


def usage_from_result(result: dict[str, Any]) -> dict[str, int | None]:
    prompt = completion = total = 0
    available = False
    for message in result.get("messages", []):
        usage = getattr(message, "usage_metadata", None)
        if not isinstance(usage, dict):
            continue
        available = True
        prompt += int(usage.get("input_tokens", 0) or 0)
        completion += int(usage.get("output_tokens", 0) or 0)
        total += int(usage.get("total_tokens", 0) or 0)
    return {
        "promptTokens": prompt if available else None,
        "completionTokens": completion if available else None,
        "totalTokens": total if available else None,
    }


def generation_benchmark(
    retrievers: dict[str, Any],
    cases: list[dict[str, Any]],
    chunks: list[dict[str, Any]],
) -> dict[str, dict[str, Any]]:
    from langchain_core.messages import AIMessage, HumanMessage
    from graph.graph_builder import AgentGraphBuilder
    from services.premium_service import PremiumService
    from services.retrieval_service import RetrievalService
    from tools.insurance_rag_tool import InsuranceRAGTool
    from tools.premium_calculator_tool import PremiumCalculatorTool

    chunk_lookup = {item["chunk_id"]: item["text"] for item in chunks}
    graphs = {}
    for engine, retriever in retrievers.items():
        rag_tool = InsuranceRAGTool(service=RetrievalService(retriever=retriever))
        premium_tool = PremiumCalculatorTool(premium_service=PremiumService())
        graphs[engine] = AgentGraphBuilder(tools=[rag_tool, premium_tool])

    outputs = {"langchain": [], "llamaindex": []}
    selected = [case for case in cases if case.get("generation")]
    for index, case in enumerate(selected):
        order = ["langchain", "llamaindex"] if index % 2 == 0 else ["llamaindex", "langchain"]
        history = [
            HumanMessage(content=item["content"])
            if item["role"] == "user" else AIMessage(content=item["content"])
            for item in case.get("history", [])
        ]
        for engine in order:
            started = time.perf_counter()
            error = None
            try:
                result = graphs[engine].invoke(
                    user_message=case["question"],
                    session_id=f"benchmark-{engine}-{case['caseId']}",
                    history_messages=history,
                    execution_id=f"benchmark-{engine}-{case['caseId']}",
                )
                answer = ""
                for message in reversed(result.get("messages", [])):
                    if isinstance(message, AIMessage) and isinstance(message.content, str) and message.content.strip():
                        answer = message.content.strip()
                        break
                retrieved = result.get("retrieved_docs", [])
                context = "\n".join(str(item.get("content", "")) for item in retrieved)
                sources = []
                for item in retrieved:
                    content = str(item.get("content", ""))
                    matched_chunk = next(
                        (chunk_id for chunk_id, text in chunk_lookup.items() if content == text),
                        None,
                    )
                    sources.append({
                        "documentName": item.get("source_name"),
                        "page": item.get("source_page"),
                        "chunkId": matched_chunk,
                        "score": item.get("similarity_score"),
                    })
                quality = answer_quality(answer, context, case["answerKey"])
                usage = usage_from_result(result)
                route = [item.get("tool_name") for item in result.get("tool_results", [])]
            except Exception as exc:
                answer = ""
                sources = []
                quality = {
                    "correctness": "Incorrect",
                    "faithfulnessProxy": False,
                    "hallucinationProxy": False,
                }
                usage = {"promptTokens": None, "completionTokens": None, "totalTokens": None}
                route = []
                error = f"{type(exc).__name__}: {exc}"
            elapsed_ms = round((time.perf_counter() - started) * 1000, 3)
            outputs[engine].append({
                "caseId": case["caseId"],
                "question": case["question"],
                "latencyMs": elapsed_ms,
                "answer": answer,
                "sources": sources,
                "toolRoute": route,
                "quality": quality,
                "tokenUsage": usage,
                "error": error,
            })
            print(f"generation {engine} {case['caseId']}", flush=True)
    return {
        engine: {
            "engine": engine,
            "model": "deepseek-chat",
            "temperature": 0.0,
            "sampleSize": len(results),
            "runsPerCase": 1,
            "cases": results,
        }
        for engine, results in outputs.items()
    }


def generation_summary(payload: dict[str, Any]) -> dict[str, Any]:
    cases = payload["cases"]
    successful = [case for case in cases if not case["error"]]
    counts = {name: 0 for name in ("Correct", "Partial", "Incorrect")}
    for case in successful:
        counts[case["quality"]["correctness"]] += 1
    tokens = [case["tokenUsage"]["totalTokens"] for case in successful if case["tokenUsage"]["totalTokens"] is not None]
    return {
        "sampleSize": len(cases),
        "successful": len(successful),
        "errors": len(cases) - len(successful),
        "correct": counts["Correct"],
        "partial": counts["Partial"],
        "incorrect": counts["Incorrect"],
        "faithfulnessProxyRate": round(statistics.fmean(case["quality"]["faithfulnessProxy"] for case in successful), 4) if successful else None,
        "hallucinationProxyRate": round(statistics.fmean(case["quality"]["hallucinationProxy"] for case in successful), 4) if successful else None,
        "latencyMs": latency_summary([case["latencyMs"] for case in successful]),
        "tokenUsage": {
            "available": bool(tokens),
            "total": sum(tokens) if tokens else None,
            "mean": round(statistics.fmean(tokens), 2) if tokens else None,
            "p50": round(statistics.median(tokens), 2) if tokens else None,
            "p95": percentile([float(value) for value in tokens], 0.95),
        },
    }


def environment_versions() -> dict[str, Any]:
    import importlib.metadata as metadata
    packages = [
        "langchain-core", "langchain-community", "llama-index-core",
        "llama-index-vector-stores-faiss", "sentence-transformers",
        "faiss-cpu", "torch", "transformers",
    ]
    return {
        "platform": platform.platform(),
        "python": platform.python_version(),
        "processor": platform.processor(),
        "packages": {name: metadata.version(name) for name in packages},
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--cases", type=Path, default=DEFAULT_CASES)
    parser.add_argument("--output-dir", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument("--skip-generation", action="store_true")
    args = parser.parse_args()
    if not args.skip_generation and not os.getenv("DEEPSEEK_API_KEY", "").strip():
        raise RuntimeError("DEEPSEEK_API_KEY is required but was not exposed to this process")

    definition = json.loads(args.cases.read_text(encoding="utf-8"))
    cases = definition["cases"]
    if len(cases) != 50:
        raise RuntimeError(f"frozen benchmark must contain 50 cases, got {len(cases)}")
    args.output_dir.mkdir(parents=True, exist_ok=True)
    corpus_dir = args.output_dir / "corpus"
    corpus_dir.mkdir(parents=True, exist_ok=True)
    corpus_text = CORPUS_FILE.read_text(encoding="utf-8")
    corpus_copy = corpus_dir / CORPUS_FILE.name
    corpus_copy.write_text(corpus_text, encoding="utf-8")
    chunks = fixed_chunks(corpus_text, CORPUS_FILE.name)
    if len(chunks) != 5:
        raise RuntimeError(f"fixed chunk contract changed: expected 5, got {len(chunks)}")
    write_json(args.output_dir / "benchmark_cases.json", definition)
    with (args.output_dir / "benchmark_chunks.jsonl").open("w", encoding="utf-8", newline="\n") as stream:
        for item in chunks:
            stream.write(json.dumps(item, ensure_ascii=False) + "\n")

    retrievers, build = build_engines(chunks, args.output_dir / "indexes")
    retrievals = {}
    for engine in ("langchain", "llamaindex"):
        retrievals[engine] = retrieval_benchmark(engine, retrievers[engine], cases)
        write_json(args.output_dir / f"retrieval_results_{engine}.json", retrievals[engine])

    generations = {
        engine: {"engine": engine, "sampleSize": 0, "cases": []}
        for engine in ("langchain", "llamaindex")
    }
    if not args.skip_generation:
        generations = generation_benchmark(retrievers, cases, chunks)
    for engine in ("langchain", "llamaindex"):
        write_json(args.output_dir / f"generation_results_{engine}.json", generations[engine])

    summary = {
        "generatedAt": datetime.now(timezone.utc).isoformat(),
        "environment": environment_versions(),
        "fairness": {
            "identicalCorpus": True,
            "identicalChunks": True,
            "identicalEmbedding": True,
            "identicalTopK": True,
            "identicalPromptAndModel": True,
            "onlyIntendedVariable": "retriever / engine implementation",
        },
        "corpus": {
            "documentCount": 1,
            "documents": [CORPUS_FILE.name],
            "chunkCount": len(chunks),
            "caseCount": len(cases),
            "retrievalEvaluatedCases": sum(case.get("retrievalEvaluated", True) for case in cases),
            "generationCases": sum(bool(case.get("generation")) for case in cases),
        },
        "build": build,
        "retrieval": {
            engine: {
                **retrieval_metrics(retrievals[engine]),
                "latencyMs": retrievals[engine]["latencyMs"],
            }
            for engine in ("langchain", "llamaindex")
        },
        "generation": {
            engine: generation_summary(generations[engine])
            for engine in ("langchain", "llamaindex")
        },
        "endToEnd": {"status": "PENDING", "reason": "run_e2e_benchmark.py not executed yet"},
        "limitations": [
            "The controlled corpus has one reliably extractable tracked TXT document.",
            "The tracked PDFs were excluded because one extracts only a garbled title and one has no extractable text.",
            "Generation uses one real DeepSeek run per selected case and is a small-sample comparison, not a stability distribution.",
            "Faithfulness and hallucination are deterministic answer-key proxies, not human review or LLM-as-judge.",
            "Per-phase embedding/build timing and peak RSS are unavailable; only total build, load, and persisted size are reported.",
            "No currency cost is calculated because current pricing is not sourced by the runtime.",
        ],
    }
    write_json(args.output_dir / "benchmark_summary.json", summary)
    print(json.dumps(summary, ensure_ascii=True, indent=2), flush=True)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
