"""One-command, non-destructive visible-boss dataset generation."""
import argparse
import os
import tempfile

from build_boss_context import build_sidecar
from enrich_boss_context import enrich_processed_dataset


def generate(raw_dir, processed_dir, output_dir, audit_path, minimum_samples=100):
    if os.path.exists(output_dir):
        raise FileExistsError(f"Refusing to overwrite output dataset: {output_dir}")
    with tempfile.TemporaryDirectory(prefix="boss-sidecar-") as temporary:
        sidecar = os.path.join(temporary, "boss_context_v1.parquet")
        build_sidecar(raw_dir, sidecar, audit_path, minimum_samples)
        enrich_processed_dataset(processed_dir, sidecar, output_dir)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("raw_dir"); parser.add_argument("processed_dir"); parser.add_argument("output_dir")
    parser.add_argument("--audit-path", required=True); parser.add_argument("--minimum-samples", type=int, default=100)
    arguments = parser.parse_args()
    generate(arguments.raw_dir, arguments.processed_dir, arguments.output_dir, arguments.audit_path, arguments.minimum_samples)
