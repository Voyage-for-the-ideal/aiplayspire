"""Pure rules and a staging-only join for boss-context enrichment.

The resolver/audit job supplies ``resolved_context``.  This module never reads
combat outcomes, including damage_taken, victory, killed_by, or floor_reached.
"""

import glob
import json
import os
import shutil
import tempfile

import pandas as pd

from boss_context import BOSS_RESOLVER_VERSION, BOSS_SCHEMA_VERSION, canonicalize_boss_name


def visible_boss_for_sample(floor, decision_type, ascension, resolved_context):
    """Return the player-visible boss for an already reconstructed snapshot."""
    if int(ascension) < 15:
        return "UNKNOWN_BOSS"
    if int(floor) == 0 or decision_type == "boss_relic":
        return "NO_BOSS"
    if decision_type in {"boss_card_reward", "boss_reward"}:
        return "NO_BOSS"
    floor = int(floor)
    if floor <= 16:
        return canonicalize_boss_name(resolved_context["act1_boss"])
    if floor <= 33:
        return canonicalize_boss_name(resolved_context["act2_boss"])
    if floor <= 50:
        return canonicalize_boss_name(resolved_context["act3_boss"])
    return "Corrupt Heart"


def enrich_processed_dataset(source_dir, sidecar_path, output_dir):
    """Join a resolved sidecar into a *new* parquet directory.

    ``output_dir`` must not already exist: publishing/replacing a production
    dataset is intentionally a separate, explicit operation.
    """
    if os.path.exists(output_dir):
        raise FileExistsError(f"Refusing to overwrite output directory: {output_dir}")
    sidecar = pd.read_parquet(sidecar_path)
    required = {"run_id", "act1_boss", "act2_boss", "act3_boss", "resolver_status"}
    missing = required.difference(sidecar.columns)
    if missing:
        raise ValueError(f"Boss sidecar is incomplete: {sorted(missing)}")
    if sidecar["run_id"].duplicated().any():
        raise ValueError("Boss sidecar contains duplicate run IDs")
    allowed_statuses = {"resolved", "below_a15_unknown"}
    if not sidecar["resolver_status"].isin(allowed_statuses).all():
        raise ValueError("Boss sidecar contains unsupported resolver statuses")
    contexts = sidecar.set_index("run_id")[['act1_boss', 'act2_boss', 'act3_boss']].to_dict("index")
    source_files = sorted(glob.glob(os.path.join(source_dir, "*_valid_chunk_*.parquet")))
    if not source_files:
        raise ValueError(f"No processed parquet shards found in {source_dir}")

    staging = tempfile.mkdtemp(prefix="boss-context-", dir=os.path.dirname(output_dir) or None)
    try:
        distributions = {}
        for path in source_files:
            frame = pd.read_parquet(path)
            missing_runs = set(frame["run_id"]).difference(contexts)
            if missing_runs:
                raise ValueError(f"Boss sidecar is missing {len(missing_runs)} run IDs")
            frame["visible_boss"] = [
                visible_boss_for_sample(row.floor, row.decision_type, row.ascension, contexts[row.run_id])
                for row in frame.itertuples(index=False)
            ]
            high_asc_unknown = (frame["ascension"] >= 15) & (frame["visible_boss"] == "UNKNOWN_BOSS")
            if high_asc_unknown.any():
                raise ValueError("Enrichment produced UNKNOWN_BOSS for A15+; do not publish")
            for boss, count in frame["visible_boss"].value_counts().items():
                distributions[boss] = distributions.get(boss, 0) + int(count)
            frame.to_parquet(os.path.join(staging, os.path.basename(path)), index=False)

        manifest_path = os.path.join(source_dir, "dataset_manifest.json")
        with open(manifest_path, encoding="utf-8") as handle:
            manifest = json.load(handle)
        manifest["boss_context"] = {
            "schema_version": BOSS_SCHEMA_VERSION,
            "resolver_version": BOSS_RESOLVER_VERSION,
            "below_a15_policy": "UNKNOWN_BOSS",
            "distributions": {"samples_by_visible_boss": distributions},
        }
        with open(os.path.join(staging, "dataset_manifest.json"), "w", encoding="utf-8") as handle:
            json.dump(manifest, handle, indent=2, sort_keys=True)
        os.replace(staging, output_dir)
    except Exception:
        shutil.rmtree(staging, ignore_errors=True)
        raise
