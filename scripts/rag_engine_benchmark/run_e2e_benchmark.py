"""Exercise selected frozen benchmark cases through the public Spring API."""

from __future__ import annotations

import argparse
import json
import math
import statistics
import time
import urllib.error
import urllib.request
import uuid
from datetime import datetime, timezone
from pathlib import Path
from typing import Any


REPOSITORY_ROOT = Path(__file__).resolve().parents[2]
DEFAULT_CASES = REPOSITORY_ROOT / "evidence" / "rag_engine_benchmark" / "cases.json"
DEFAULT_OUTPUT = REPOSITORY_ROOT / "artifacts" / "rag_engine_benchmark"


def request_json(
    method: str,
    url: str,
    *,
    body: dict[str, Any] | None = None,
    headers: dict[str, str] | None = None,
    timeout: float = 70.0,
) -> tuple[int, dict[str, Any]]:
    payload = None if body is None else json.dumps(body, ensure_ascii=False).encode("utf-8")
    request = urllib.request.Request(url, data=payload, method=method)
    request.add_header("Accept", "application/json")
    if payload is not None:
        request.add_header("Content-Type", "application/json; charset=utf-8")
    for key, value in (headers or {}).items():
        request.add_header(key, value)
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            return response.status, json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as error:
        content = error.read().decode("utf-8", errors="replace")
        try:
            parsed = json.loads(content)
        except json.JSONDecodeError:
            parsed = {"code": "NON_JSON_ERROR", "message": content[:500]}
        return error.code, parsed


def percentile(values: list[float], fraction: float) -> float | None:
    if not values:
        return None
    ordered = sorted(values)
    position = (len(ordered) - 1) * fraction
    lower = math.floor(position)
    upper = math.ceil(position)
    if lower == upper:
        return round(ordered[lower], 3)
    return round(ordered[lower] + (ordered[upper] - ordered[lower]) * (position - lower), 3)


def quality(answer: str, key: dict[str, Any]) -> dict[str, Any]:
    required_all = key.get("requiredAll", [])
    required_any = key.get("requiredAny", [])
    forbidden = key.get("forbiddenClaims", [])
    matched_all = [item for item in required_all if item in answer]
    matched_any = [item for item in required_any if item in answer]
    forbidden_hits = [item for item in forbidden if item in answer]
    if len(matched_all) == len(required_all) and (not required_any or matched_any) and not forbidden_hits:
        verdict = "Correct"
    elif matched_all or matched_any:
        verdict = "Partial"
    else:
        verdict = "Incorrect"
    return {"correctness": verdict, "forbiddenHits": forbidden_hits}


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--engine", choices=("langchain", "llamaindex"), required=True)
    parser.add_argument("--base-url", default="http://127.0.0.1:8181")
    parser.add_argument("--cases", type=Path, default=DEFAULT_CASES)
    parser.add_argument("--output-dir", type=Path, default=DEFAULT_OUTPUT)
    args = parser.parse_args()

    definition = json.loads(args.cases.read_text(encoding="utf-8"))
    cases = [case for case in definition["cases"] if case.get("e2e")]
    suffix = uuid.uuid4().hex[:12]
    username = f"rag_benchmark_{args.engine}_{suffix}"
    password = f"Benchmark-{uuid.uuid4().hex}-A9!"
    status, registered = request_json(
        "POST", f"{args.base_url}/api/v1/auth/register",
        body={"username": username, "password": password},
    )
    if status != 201 or registered.get("code") != "OK":
        raise RuntimeError(f"registration failed: HTTP {status}")
    status, logged_in = request_json(
        "POST", f"{args.base_url}/api/v1/auth/login",
        body={"username": username, "password": password},
    )
    if status != 200 or logged_in.get("code") != "OK":
        raise RuntimeError(f"login failed: HTTP {status}")
    auth = {"Authorization": f"Bearer {logged_in['data']['accessToken']}"}

    results = []
    for case in cases:
        status, created = request_json(
            "POST", f"{args.base_url}/api/v1/conversations",
            body={"title": f"RAG benchmark {case['caseId']}"}, headers=auth,
        )
        if status != 201 or created.get("code") != "OK":
            raise RuntimeError(f"conversation failed for {case['caseId']}: HTTP {status}")
        trace_id = f"RB{args.engine[:2].upper()}{case['caseId'].replace('-', '')}"
        headers = {
            **auth,
            "Idempotency-Key": str(uuid.uuid4()),
            "X-Trace-Id": trace_id,
        }
        started = time.perf_counter()
        try:
            status, envelope = request_json(
                "POST", f"{args.base_url}/api/v1/chat/messages",
                body={
                    "conversationId": created["data"]["conversationId"],
                    "message": case["question"],
                },
                headers=headers,
            )
            transport_error = None
        except (TimeoutError, urllib.error.URLError) as exc:
            status, envelope = 0, {"code": "CLIENT_TIMEOUT", "message": str(exc)}
            transport_error = type(exc).__name__
        elapsed_ms = round((time.perf_counter() - started) * 1000, 3)
        data = envelope.get("data") if isinstance(envelope.get("data"), dict) else {}
        answer = str(data.get("answer", ""))
        sources = data.get("sources") if isinstance(data.get("sources"), list) else []
        success = status == 200 and envelope.get("code") == "OK"
        results.append({
            "caseId": case["caseId"],
            "httpStatus": status,
            "success": success,
            "timeout": status in (0, 504) or envelope.get("code") in ("CLIENT_TIMEOUT", "AI_TIMEOUT"),
            "errorCode": None if success else envelope.get("code"),
            "transportError": transport_error,
            "latencyMs": elapsed_ms,
            "answer": answer,
            "sources": sources,
            "quality": quality(answer, case["answerKey"]),
        })
        print(f"e2e {args.engine} {case['caseId']} HTTP={status}", flush=True)

    successful = [item for item in results if item["success"]]
    latencies = [item["latencyMs"] for item in successful]
    summary = {
        "engine": args.engine,
        "generatedAt": datetime.now(timezone.utc).isoformat(),
        "sampleSize": len(results),
        "successful": len(successful),
        "successRate": round(len(successful) / len(results), 4),
        "timeoutCount": sum(item["timeout"] for item in results),
        "errorCount": sum(not item["success"] and not item["timeout"] for item in results),
        "correct": sum(item["quality"]["correctness"] == "Correct" for item in successful),
        "partial": sum(item["quality"]["correctness"] == "Partial" for item in successful),
        "incorrect": sum(item["quality"]["correctness"] == "Incorrect" for item in successful),
        "latencyMs": {
            "mean": round(statistics.fmean(latencies), 3) if latencies else None,
            "p50": round(statistics.median(latencies), 3) if latencies else None,
            "p95": percentile(latencies, 0.95),
            "min": round(min(latencies), 3) if latencies else None,
            "max": round(max(latencies), 3) if latencies else None,
        },
    }
    payload = {"summary": summary, "cases": results}
    args.output_dir.mkdir(parents=True, exist_ok=True)
    (args.output_dir / f"e2e_results_{args.engine}.json").write_text(
        json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    print(json.dumps(summary, ensure_ascii=True, indent=2), flush=True)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
