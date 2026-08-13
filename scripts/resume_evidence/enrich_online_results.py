"""Enrich an existing online Evidence result with trace-correlated Java timings."""

from __future__ import annotations

import argparse
import json
import math
import re
import statistics
import tempfile
from pathlib import Path
from typing import Any


REPOSITORY_ROOT = Path(__file__).resolve().parents[2]
DEFAULT_RESULTS = REPOSITORY_ROOT / "artifacts" / "resume_evidence" / "online_ai_results.json"
DEFAULT_SUMMARY = REPOSITORY_ROOT / "artifacts" / "resume_evidence" / "online_ai_summary.json"
DEFAULT_JAVA_LOG = (
    Path(tempfile.gettempdir()) / "insurance-resume-evidence" / "java.stdout.log"
)
JAVA_TIMING_PATTERN = re.compile(
    r"event=python_agent_call service=java .*?status=([A-Z]+) "
    r"durationMs=(\d+) errorCode=([A-Z0-9_]+)"
)


def parse_java_timings(path: Path) -> dict[str, dict[str, Any]]:
    timings: dict[str, dict[str, Any]] = {}
    if not path.exists():
        return timings
    for line in path.read_text(encoding="utf-8", errors="replace").splitlines():
        marker = "traceId="
        if marker not in line:
            continue
        trace = line.split(marker, 1)[1].split(None, 1)[0]
        match = JAVA_TIMING_PATTERN.search(line)
        if match:
            timings[trace] = {
                "duration_ms": int(match.group(2)),
                "status": match.group(1),
                "error_code": match.group(3),
            }
    return timings


def timing_summary(values: list[float]) -> dict[str, float | int | None]:
    if not values:
        return {"count": 0, "mean": None, "p50": None, "p95": None, "min": None, "max": None}
    ordered = sorted(values)
    p95_index = min(len(ordered) - 1, math.ceil((len(ordered) - 1) * 0.95))
    return {
        "count": len(ordered),
        "mean": round(statistics.fmean(ordered), 2),
        "p50": round(statistics.median(ordered), 2),
        "p95": round(ordered[p95_index], 2),
        "min": round(ordered[0], 2),
        "max": round(ordered[-1], 2),
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--results", type=Path, default=DEFAULT_RESULTS)
    parser.add_argument("--summary", type=Path, default=DEFAULT_SUMMARY)
    parser.add_argument("--java-log", type=Path, default=DEFAULT_JAVA_LOG)
    args = parser.parse_args()

    result_document = json.loads(args.results.read_text(encoding="utf-8"))
    summary = json.loads(args.summary.read_text(encoding="utf-8"))
    timings = parse_java_timings(args.java_log)
    matched = 0
    for case in result_document["cases"]:
        for turn in case["turns"]:
            timing = timings.get(turn["trace_id"])
            if timing is None:
                continue
            turn["java_to_python_duration_ms"] = timing["duration_ms"]
            turn["java_to_python_status"] = timing["status"]
            turn["java_to_python_error_code"] = timing["error_code"]
            matched += 1

    turns = [turn for case in result_document["cases"] for turn in case["turns"]]
    request_count = len(turns)
    summary["java_to_python_duration_available"] = matched == request_count
    summary["java_to_python_duration_records"] = matched
    summary["python_agent_duration_available"] = False
    summary["timing_field_notes"] = {
        "end_to_end_duration_ms": "Measured by the Evidence HTTP client around the public Java API call.",
        "java_to_python_duration_ms": "Parsed from Java python_agent_call logs by TraceId.",
        "python_http_duration_ms": "Parsed from FastAPI request completion logs by TraceId.",
        "python_agent_duration_ms": "Not exposed by the public Java API or current structured logs.",
    }
    summary["java_to_python_latency_ms"] = timing_summary([
        turn["java_to_python_duration_ms"]
        for turn in turns
        if turn["java_to_python_duration_ms"] is not None
    ])
    summary["python_http_latency_ms"] = timing_summary([
        turn["python_http_duration_ms"]
        for turn in turns
        if turn["python_http_duration_ms"] is not None
    ])
    set_equivalent_count = sum(
        set(turn["actual_tool_route"]) == set(turn["expected_tool_route"])
        for turn in turns
    )
    summary["supplemental_route_diagnostic"] = {
        "name": "tool-set equivalence ignoring order and duplicate calls",
        "correct": set_equivalent_count,
        "total": request_count,
        "rate": round(set_equivalent_count / request_count, 4),
        "is_primary_metric": False,
        "note": "The primary route metric remains exact ordered-sequence equality.",
    }
    summary["quality_rule_review"] = {
        "raw_passed": summary["quality_passed_turns"],
        "raw_total": request_count,
        "raw_rate": summary["quality_rule_pass_rate"],
        "manual_rule_false_negative_case_ids": ["RAG-06", "UNKNOWN-02"],
        "note": "Both stored answers satisfy the intended fact/safety behavior but fail literal matching; the raw score is preserved and is not relabeled as answer accuracy.",
    }
    summary["limitations"] = [
        "Small-sample local test; results do not represent a production SLA.",
        "Python Agent duration is not exposed by the public Java API or current structured logs.",
        "Source values are captured from the public API response without model-text parsing.",
    ]

    args.results.write_text(
        json.dumps(result_document, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    args.summary.write_text(
        json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    print(json.dumps({"requests": request_count, "java_timings_matched": matched}))
    return 0 if matched == request_count else 1


if __name__ == "__main__":
    raise SystemExit(main())
