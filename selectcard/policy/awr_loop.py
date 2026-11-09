"""Explicit long-running AWR job, using a user-supplied isolated collector.

Collector command is a JSON argument array, never interpreted by a shell.
Placeholders: {checkpoint}, {trace_dir}, {character}, {runs}, {ascension}.
This module is never started automatically by training or normal gameplay.
"""
import argparse
import json
from pathlib import Path
import subprocess
import sys
from .contracts import CHARACTERS
from .data import build_examples, load_examples, read_jsonl


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--data", type=Path, required=True)
    parser.add_argument("--checkpoint", type=Path, required=True)
    parser.add_argument("--collector-command", type=Path, required=True)
    parser.add_argument("--output-dir", type=Path, required=True)
    parser.add_argument("--iterations", type=int, default=5)
    parser.add_argument("--runs-per-character", type=int, default=50)
    parser.add_argument("--device", default="cpu")
    args = parser.parse_args()
    if not 1 <= args.iterations <= 5 or args.runs_per_character <= 0:
        parser.error("iterations must be 1..5; positive runs-per-character")
    template = json.loads(args.collector_command.read_text(encoding="utf-8"))
    if not isinstance(template, list) or not template or not all(isinstance(v, str) for v in template):
        parser.error("collector-command must be a JSON array of arguments")
    for required in ("{checkpoint}", "{trace_dir}", "{character}", "{runs}"):
        if not any(required in value for value in template):
            parser.error("missing collector placeholder " + required)
    args.output_dir.mkdir(parents=True, exist_ok=False)
    initial = load_examples(args.data)
    checkpoint = args.checkpoint.resolve()
    collected = []
    for iteration in range(args.iterations):
        directory = args.output_dir / str(iteration + 1)
        directory.mkdir()
        paths = []
        for character in CHARACTERS:
            traces = directory / character
            command = [value.format(checkpoint=checkpoint, trace_dir=traces.resolve(), character=character,
                                    runs=args.runs_per_character, ascension=20) for value in template]
            subprocess.run(command, check=True, shell=False)
            actual = list(traces.glob("*.jsonl"))
            ends = [r for p in actual for r in read_jsonl(p) if r.get("record_type") == "run_end" and r["outcome"].get("terminal_known")]
            starts = [r for p in actual for r in read_jsonl(p) if r.get("record_type") == "run_start"]
            if len(ends) != args.runs_per_character or any(r.get("character") != character or r.get("ascension") != 20 or not r.get("seed_group") for r in starts):
                raise ValueError("collector did not produce the requested completed character/A20 cohort")
            if any(not r.get("metadata", {}).get("runtime") for r in starts):
                raise ValueError("collector missing actual runtime manifest")
            paths.extend(actual)
        examples, stats = build_examples(paths)
        collected.extend(examples)
        data = directory / "examples.jsonl"
        with data.open("x", encoding="utf-8") as handle:
            for row in initial + collected:
                handle.write(json.dumps(row, ensure_ascii=False) + "\n")
        (directory / "collection.json").write_text(json.dumps(stats, indent=2), encoding="utf-8")
        training = directory / "training"
        subprocess.run([sys.executable, "-m", "selectcard.policy.train", "--data", str(data), "--output-dir", str(training),
                        "--mode", "awr", "--stage", "confirm", "--checkpoint", str(checkpoint), "--device", args.device], check=True)
        checkpoint = (training / "policy.pt").resolve()


if __name__ == "__main__":
    main()
