"""Split Java 8 HPROF CPU samples inside turn-state fingerprint construction.

Use cpu=samples,depth=96,cutoff=0,thread=y and a normal engine shutdown.
This analyzes stack samples, not per-field clocks. Millisecond estimates are
sample shares scaled by a separately measured, unprofiled fingerprint median.
"""
import argparse
from collections import Counter, defaultdict
import json
from pathlib import Path
import re

TRACES = re.compile(r"^TRACE (\d+):[^\n]*\n((?:\t[^\n]*\n)+)", re.MULTILINE)
ROW = re.compile(r"\s*\d+\s+[\d.]+%\s+[\d.]+%\s+(\d+)\s+(\d+)\s+")
ENCODERS = {"writeString", "writeAscii", "writeInteger", "writeNumber", "writeBytes",
            "writeByte", "writeCodePoint", "writeUnicodeEscape", "uuidAlias", "isPlainAscii"}
CANONICAL = "battleaimod.search.SearchStateKey$CanonicalHasher."


def classify(frames):
    methods = [frame.strip().split("(", 1)[0] for frame in frames]
    if ("battleaimod.battleai.BattleAiController.registerTurnState" not in methods
            or "battleaimod.search.SearchStateKey.fromSaveState" not in methods):
        return None
    # The deepest stage wins: hashing is nested inside buffered encoding, which
    # in turn is nested inside traversal. These buckets are mutually exclusive.
    if any(m.startswith("com.google.common.hash.") or m == CANONICAL + "flush" for m in methods):
        return "hash"
    if (any(CANONICAL + name in methods for name in ENCODERS)
            or any(m in methods for m in ["battleaimod.search.SaveStateFields$Plan.encode",
                "java.lang.Float.toString", "java.lang.Double.toString", "java.util.UUID.toString"])):
        return "encoding"
    return "traversal"


def analyze(contents, state_key_ms=None):
    traces = {int(m[1]): m[2].splitlines() for m in TRACES.finditer(contents)}
    # Dumps are cumulative; use the last complete CPU table, never sum dumps.
    tables = list(re.finditer(r"CPU SAMPLES BEGIN \(total = (\d+)\)[^\n]*\n(.*?)CPU SAMPLES END",
                              contents, re.DOTALL))
    if not tables:
        raise ValueError("missing complete CPU SAMPLES table")
    table = tables[-1]
    counts, top = Counter(), defaultdict(Counter)
    rows_total = 0
    for line in table[2].splitlines():
        match = ROW.match(line)
        if not match:
            continue
        count, trace_id = map(int, match.groups())
        rows_total += count
        if trace_id not in traces:
            raise ValueError("missing trace " + str(trace_id))
        frames = traces[trace_id]
        bucket = classify(frames)
        if bucket:
            counts[bucket] += count
            top[bucket][frames[0].strip()] += count
    if rows_total != int(table[1]):
        raise ValueError("CPU table is truncated/cut off; use cutoff=0")
    total = sum(counts.values())
    if not total:
        raise ValueError("no turn-state fingerprint samples; check depth and snapshot workload")
    result = {"method": "exclusive_stack_samples", "all_thread_samples": rows_total,
              "fingerprint_samples": total, "unprofiled_state_key_ms": state_key_ms,
              "buckets": {}}
    for bucket in ["traversal", "encoding", "hash"]:
        share = counts[bucket] / total
        result["buckets"][bucket] = {"samples": counts[bucket], "share": share,
            "estimated_ms": None if state_key_ms is None else share * state_key_ms,
            "top_frames": top[bucket].most_common(10)}
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("profile", type=Path)
    parser.add_argument("--state-key-ms", type=float)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    result = analyze(args.profile.read_text(encoding="utf-8", errors="replace"), args.state_key_ms)
    rendered = json.dumps(result, indent=2)
    if args.output:
        args.output.write_text(rendered, encoding="utf-8")
    print(rendered)


if __name__ == "__main__":
    main()
