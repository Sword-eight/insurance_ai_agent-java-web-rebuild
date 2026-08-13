"""Run the frozen Resume Evidence set through the public Spring Boot HTTP API."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import statistics
import tempfile
import time
import urllib.error
import urllib.request
import uuid
from datetime import datetime, timezone
from pathlib import Path
from typing import Any


REPOSITORY_ROOT = Path(__file__).resolve().parents[2]
DEFAULT_CASES = REPOSITORY_ROOT / "evidence" / "resume_evidence" / "cases.json"
DEFAULT_OUTPUT = REPOSITORY_ROOT / "artifacts" / "resume_evidence"
DEFAULT_RUNTIME = Path(tempfile.gettempdir()) / "insurance-resume-evidence"
# Windows redirected logs may mojibake the Chinese verb between the stable
# ASCII marker and tool name. Match the stable boundaries without decoding it.
TOOL_PATTERN = re.compile(r"\[Tool\].*?: ([A-Za-z0-9_]+)\(")
HTTP_DURATION_PATTERN = re.compile(
    r"event=http_request service=python .* path=/internal/v1/agent/chat "
    r"status=(\d+) durationMs=(\d+) errorCode=([A-Z0-9_]+)"
)


def request_json(
    method: str,
    url: str,
    *,
    body: dict[str, Any] | None = None,
    headers: dict[str, str] | None = None,
    timeout: float = 70.0,
) -> tuple[int, dict[str, Any], dict[str, str]]:
    payload = None if body is None else json.dumps(body, ensure_ascii=False).encode("utf-8")
    request = urllib.request.Request(url, data=payload, method=method)
    request.add_header("Accept", "application/json")
    if payload is not None:
        request.add_header("Content-Type", "application/json; charset=utf-8")
    for key, value in (headers or {}).items():
        request.add_header(key, value)
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            return (
                response.status,
                json.loads(response.read().decode("utf-8")),
                dict(response.headers.items()),
            )
    except urllib.error.HTTPError as error:
        content = error.read().decode("utf-8", errors="replace")
        try:
            parsed = json.loads(content)
        except json.JSONDecodeError:
            parsed = {"code": "NON_JSON_ERROR", "message": content[:500]}
        return error.code, parsed, dict(error.headers.items())


def normalize_number_text(value: str) -> str:
    return value.replace(",", "").replace("，", "")


def quality_result(turn: dict[str, Any], answer: str) -> tuple[bool, list[str]]:
    failures: list[str] = []
    normalized_answer = normalize_number_text(answer)
    for keyword in turn.get("required_all", []):
        if keyword not in answer:
            failures.append(f"missing required keyword: {keyword}")
    required_any = turn.get("required_any", [])
    if required_any and not any(keyword in answer for keyword in required_any):
        failures.append("none of required_any matched: " + ", ".join(required_any))
    for number in turn.get("expected_numbers", []):
        if normalize_number_text(str(number)) not in normalized_answer:
            failures.append(f"missing expected number: {number}")
    for forbidden in turn.get("forbidden_claims", []):
        if forbidden in answer:
            failures.append(f"forbidden claim present: {forbidden}")
    return not failures, failures


def trace_id(case_id: str, turn_index: int) -> str:
    digest = hashlib.sha256(f"{case_id}:{turn_index}".encode("utf-8")).hexdigest()[:20]
    return f"RE{digest}"


def parse_trace_log(log_path: Path, trace: str) -> dict[str, Any]:
    if not log_path.exists():
        return {"actual_tool_route": [], "python_http_duration_ms": None, "python_error_code": None}
    matching = [
        line for line in log_path.read_text(encoding="utf-8", errors="replace").splitlines()
        if f"traceId={trace}" in line
    ]
    route: list[str] = []
    python_duration = None
    python_error_code = None
    for line in matching:
        tool_match = TOOL_PATTERN.search(line)
        if tool_match:
            route.append(tool_match.group(1))
        duration_match = HTTP_DURATION_PATTERN.search(line)
        if duration_match:
            python_duration = int(duration_match.group(2))
            python_error_code = duration_match.group(3)
    return {
        "actual_tool_route": route,
        "python_http_duration_ms": python_duration,
        "python_error_code": python_error_code,
    }


def create_users(base_url: str, count: int) -> list[str]:
    tokens: list[str] = []
    run_suffix = uuid.uuid4().hex[:12]
    for index in range(count):
        username = f"resume_evidence_{run_suffix}_{index}"
        password = f"Evidence-{uuid.uuid4().hex}-A9!"
        status, registered, _ = request_json(
            "POST", f"{base_url}/api/v1/auth/register",
            body={"username": username, "password": password},
        )
        if status != 201 or registered.get("code") != "OK":
            raise RuntimeError(f"registration failed with status {status}")
        status, logged_in, _ = request_json(
            "POST", f"{base_url}/api/v1/auth/login",
            body={"username": username, "password": password},
        )
        if status != 200 or logged_in.get("code") != "OK":
            raise RuntimeError(f"login failed with status {status}")
        tokens.append(logged_in["data"]["accessToken"])
    return tokens


def percentile(values: list[float], fraction: float) -> float | None:
    if not values:
        return None
    ordered = sorted(values)
    rank = max(0, min(len(ordered) - 1, int((len(ordered) - 1) * fraction + 0.999999)))
    return round(ordered[rank], 2)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", default="http://127.0.0.1:8080")
    parser.add_argument("--cases", type=Path, default=DEFAULT_CASES)
    parser.add_argument("--output-dir", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument("--runtime-root", type=Path, default=DEFAULT_RUNTIME)
    args = parser.parse_args()

    definition = json.loads(args.cases.read_text(encoding="utf-8"))
    cases = definition["cases"]
    if len(cases) != 30:
        raise RuntimeError(f"frozen evidence set must contain exactly 30 cases, got {len(cases)}")

    args.output_dir.mkdir(parents=True, exist_ok=True)
    python_log = args.runtime_root / "python.stderr.log"
    tokens = create_users(args.base_url, 2)
    results: list[dict[str, Any]] = []

    for case_index, case in enumerate(cases):
        token = tokens[case_index % len(tokens)]
        auth_headers = {"Authorization": f"Bearer {token}"}
        create_status, created, _ = request_json(
            "POST", f"{args.base_url}/api/v1/conversations",
            body={"title": f"Evidence {case['case_id']}"}, headers=auth_headers,
        )
        if create_status != 201 or created.get("code") != "OK":
            raise RuntimeError(f"conversation creation failed for {case['case_id']}")
        conversation_id = created["data"]["conversationId"]

        case_turns: list[dict[str, Any]] = []
        for turn_index, turn in enumerate(case["turns"], start=1):
            trace = trace_id(case["case_id"], turn_index)
            headers = {
                **auth_headers,
                "Idempotency-Key": str(uuid.uuid4()),
                "X-Trace-Id": trace,
            }
            started = time.perf_counter()
            status, envelope, response_headers = request_json(
                "POST", f"{args.base_url}/api/v1/chat/messages",
                body={"conversationId": conversation_id, "message": turn["question"]},
                headers=headers,
            )
            elapsed_ms = round((time.perf_counter() - started) * 1000, 2)
            time.sleep(0.1)
            log_evidence = parse_trace_log(python_log, trace)

            http_success = status == 200 and envelope.get("code") == "OK" and envelope.get("data")
            data = envelope.get("data") if isinstance(envelope.get("data"), dict) else {}
            answer = str(data.get("answer", ""))
            sources = data.get("sources") if isinstance(data.get("sources"), list) else []
            quality_ok, quality_failures = quality_result(turn, answer)
            expected_route = turn["expected_route"]
            actual_route = log_evidence["actual_tool_route"]
            route_correct = actual_route == expected_route
            source_document = turn.get("expected_source_document")
            retrieval_hit = None
            if source_document:
                retrieval_hit = any(
                    source.get("documentName") == source_document for source in sources
                    if isinstance(source, dict)
                )

            case_turns.append({
                "turn": turn_index,
                "question": turn["question"],
                "http_status": status,
                "http_success": bool(http_success),
                "agent_final_status": "SUCCEEDED" if http_success else "FAILED",
                "trace_id": envelope.get("traceId") or response_headers.get("X-Trace-Id") or trace,
                "expected_tool_route": expected_route,
                "actual_tool_route": actual_route,
                "tool_route_correct": route_correct,
                "sources_returned": bool(sources),
                "source_count": len(sources),
                "sources": sources,
                "retrieval_hit": retrieval_hit,
                "end_to_end_duration_ms": elapsed_ms,
                "java_to_python_duration_ms": None,
                "python_agent_duration_ms": None,
                "python_http_duration_ms": log_evidence["python_http_duration_ms"],
                "error_code": None if http_success else envelope.get("code"),
                "python_error_code": log_evidence["python_error_code"],
                "failure_reason": None if http_success else envelope.get("message", "unknown failure"),
                "answer": answer,
                "quality_pass": quality_ok,
                "quality_failures": quality_failures,
            })
        results.append({
            "case_id": case["case_id"],
            "category": case["category"],
            "turns": case_turns,
            "case_success": all(turn["http_success"] for turn in case_turns),
            "case_quality_pass": all(turn["quality_pass"] for turn in case_turns),
        })
        print(f"{case['case_id']}: completed", flush=True)

    flat = [turn for case in results for turn in case["turns"]]
    successful = [turn for turn in flat if turn["http_success"]]
    latencies = [turn["end_to_end_duration_ms"] for turn in successful]
    rag_turns = [turn for turn in flat if "insurance_rag_search" in turn["expected_tool_route"]]
    actual_rag_turns = [
        turn for turn in flat
        if "insurance_rag_search" in turn["actual_tool_route"]
    ]
    source_records = [
        source for turn in flat for source in turn["sources"]
        if isinstance(source, dict)
    ]
    retrieval_checks = [
        turn["retrieval_hit"] for turn in rag_turns
        if turn["retrieval_hit"] is not None
    ]
    multi_cases = [case for case in results if case["category"] == "multi_turn"]
    failures = [
        {
            "case_id": case["case_id"],
            "category": case["category"],
            "turn": turn["turn"],
            "question": turn["question"],
            "expected_route": turn["expected_tool_route"],
            "actual_route": turn["actual_tool_route"],
            "http_success": turn["http_success"],
            "quality_failures": turn["quality_failures"],
            "error_code": turn["error_code"],
        }
        for case in results for turn in case["turns"]
        if not turn["http_success"] or not turn["tool_route_correct"] or not turn["quality_pass"]
    ]
    summary = {
        "generated_at": datetime.now(timezone.utc).isoformat(),
        "environment": "local development evidence test",
        "case_count": len(results),
        "request_count": len(flat),
        "successful_requests": len(successful),
        "request_success_rate": round(len(successful) / len(flat), 4),
        "tool_route_cases": len(flat),
        "correct_tool_routes": sum(turn["tool_route_correct"] for turn in flat),
        "tool_route_accuracy": round(sum(turn["tool_route_correct"] for turn in flat) / len(flat), 4),
        "rag_cases": len(rag_turns),
        "rag_sources_returned": sum(turn["sources_returned"] for turn in rag_turns),
        "rag_source_return_rate": round(sum(turn["sources_returned"] for turn in rag_turns) / len(rag_turns), 4),
        "rag_average_sources_per_expected_request": round(
            statistics.fmean(turn["source_count"] for turn in rag_turns), 2
        ),
        "rag_average_sources_per_nonempty_request": round(
            statistics.fmean(
                turn["source_count"] for turn in rag_turns if turn["source_count"]
            ),
            2,
        ),
        "actual_rag_requests": len(actual_rag_turns),
        "actual_rag_sources_returned": sum(
            turn["sources_returned"] for turn in actual_rag_turns
        ),
        "retrieval_hit_rate": (
            round(sum(retrieval_checks) / len(retrieval_checks), 4)
            if retrieval_checks else None
        ),
        "source_document_names": sorted({
            source.get("documentName") for source in source_records
            if isinstance(source.get("documentName"), str)
        }),
        "source_page_records": sum(source.get("page") is not None for source in source_records),
        "source_score_records": sum(source.get("score") is not None for source in source_records),
        "quality_passed_turns": sum(turn["quality_pass"] for turn in flat),
        "quality_rule_pass_rate": round(sum(turn["quality_pass"] for turn in flat) / len(flat), 4),
        "multi_turn_cases": len(multi_cases),
        "multi_turn_successes": sum(
            case["case_success"] and case["case_quality_pass"] for case in multi_cases
        ),
        "latency_ms": {
            "mean": round(statistics.fmean(latencies), 2) if latencies else None,
            "p50": round(statistics.median(latencies), 2) if latencies else None,
            "p95": percentile(latencies, 0.95),
            "min": round(min(latencies), 2) if latencies else None,
            "max": round(max(latencies), 2) if latencies else None,
        },
        "java_to_python_duration_available": False,
        "python_agent_duration_available": False,
        "python_http_duration_available": any(
            turn["python_http_duration_ms"] is not None for turn in flat
        ),
        "failure_count": len(failures),
        "failures": failures,
        "limitations": [
            "Small-sample local test; results do not represent a production SLA.",
            "The public API does not expose Java-to-Python or Python Agent duration.",
            "Source values are captured from the public API response without model-text parsing.",
        ],
    }

    raw_path = args.output_dir / "online_ai_results.json"
    summary_path = args.output_dir / "online_ai_summary.json"
    raw_path.write_text(json.dumps({"cases": results}, ensure_ascii=False, indent=2), encoding="utf-8")
    summary_path.write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(summary, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
