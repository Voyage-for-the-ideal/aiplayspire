"""Checksum final checkpoints/reports in place; never duplicate the raw corpus."""
import argparse
import json
from pathlib import Path
from .shards import file_hash


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--run-dir", type=Path, required=True)
    root = parser.parse_args().run_dir
    required = ("data/manifest.json", "data/coverage.json", "model/best.pt", "model/policy.pt", "model/last.pt", "model/metrics.json", "model/curves.csv", "model/curves.svg", "model/best-validation.json")
    if any(not (root / name).is_file() for name in required):
        raise ValueError("training incomplete; refusing to archive as completed")
    value = dict(status="complete", files={name: file_hash(root / name) for name in required}, promotion="not deployed; offline validation only")
    with (root / "archive.json").open("x", encoding="utf-8") as stream:
        json.dump(value, stream, indent=2)


if __name__ == "__main__":
    main()
