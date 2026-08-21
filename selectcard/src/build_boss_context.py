"""Build the combat-observed (seed-backed) visible-boss sidecar.

The player always knows the boss of the Act they are in once the Act map is
generated, so a run's own boss combats are historical fact, not future
outcome.  ``visible_boss`` therefore comes from the run's boss combat records
when they exist, and the seed resolver only fills the gap for runs that died
before their Act's boss:

- Act with a boss combat record -> that boss (``observed`` source)
- Act without a record (run died earlier) -> seed resolver prediction
  (``resolved`` source), so ``NO_BOSS`` never collapses into "this Act was
  lost" during training

The audit compares the resolver against observed boss combats on runs that
have them: it measures the quality of the *fallback*, not of the feature
itself.  ``damage_taken`` supplies only already-public boss identity.
"""
import argparse
import glob
import gzip
import json
import os

import pandas as pd

from boss_context import BOSS_RESOLVER_VERSION, BossContextResolver, canonicalize_boss_name
from data_contract import ascension_band
from data_pipeline import stable_run_id

_BOSS_FLOORS = {16: "act1_boss", 33: "act2_boss", 50: "act3_boss"}
_ACTS = ("act1_boss", "act2_boss", "act3_boss")

# Fallback quality gate: the resolver is only a backfill for runs that died
# before their Act's boss, so the gate measures fallback quality, not feature
# correctness (combat observations are the feature source and need no gate).
# Residual mismatches come from per-profile boss unlocks (A1-5) and historical
# build versions, which no seed-based resolver can reproduce; 90% fallback
# correctness keeps the backfill in noise territory while still failing
# structurally broken resolver ports.
FALLBACK_AGREEMENT_GATE = 0.90


def _events(path):
    opener = gzip.open if path.endswith(".gz") else open
    with opener(path, "rt", encoding="utf-8") as handle:
        payload = json.load(handle)
    for item in payload if isinstance(payload, list) else [payload]:
        yield item.get("event", item)


def _observed_bosses(event):
    """Boss combats the run actually reached; already public at decision time."""
    observed = {}
    for combat in event.get("damage_taken", []):
        field = _BOSS_FLOORS.get(int(combat.get("floor", -1)))
        if field:
            name = canonicalize_boss_name(combat.get("enemies"))
            if name != "UNKNOWN_BOSS" and field not in observed:
                observed[field] = name
    return observed


def build_sidecar(input_dir, output_path, audit_path=None, minimum_samples=100):
    resolver = BossContextResolver(); rows = []; audits = []
    for path in glob.glob(os.path.join(input_dir, "**", "*.json*"), recursive=True):
        for event in _events(path):
            ascension = int(event.get("ascension_level", 0)) if event.get("is_ascension_mode") else 0
            if ascension == 0:
                # Product policy: A0 never uses a real boss identity, even when
                # one is observable.  Status is recorded so the enrichment join
                # can distinguish it from a resolution gap.
                row = {
                    "run_id": stable_run_id(event),
                    "act1_boss": "NO_BOSS", "act2_boss": "NO_BOSS", "act3_boss": "NO_BOSS",
                    "act1_source": "a0_no_boss", "act2_source": "a0_no_boss", "act3_source": "a0_no_boss",
                    "resolver_version": BOSS_RESOLVER_VERSION,
                    "resolver_status": "a0_no_boss",
                }
                rows.append(row)
                continue
            try:
                predicted = resolver.resolve_run(event)
            except ValueError:
                predicted = None
            observed = _observed_bosses(event)
            row = {"run_id": stable_run_id(event)}
            sources = {}
            for field in _ACTS:
                if predicted is None:
                    row[field] = "UNKNOWN_BOSS"
                    sources[field] = "missing_seed"
                elif field in observed:
                    row[field] = observed[field]
                    sources[field] = "observed"
                else:
                    row[field] = predicted[field]
                    sources[field] = "resolved"
            row.update({field.replace("_boss", "_source"): sources[field] for field in _ACTS})
            row["resolver_version"] = BOSS_RESOLVER_VERSION
            if predicted is None:
                row["resolver_status"] = "missing_seed"
            elif any(sources[field] == "observed" for field in _ACTS):
                row["resolver_status"] = "observed"
            else:
                row["resolver_status"] = "resolved"
            rows.append(row)
            if ascension >= 1:
                for field, observed_boss in observed.items():
                    audits.append({
                        "act": field[3], "ascension": ascension,
                        "ascension_band": ascension_band(ascension),
                        "build_version": str(event.get("build_version", "")),
                        "predicted_boss": (
                            canonicalize_boss_name(predicted[field]) if predicted else "UNKNOWN_BOSS"
                        ),
                        "observed_boss": observed_boss,
                    })
    audit = pd.DataFrame(audits)
    if audit.empty: raise ValueError("No audit boss combats found; refusing to publish sidecar")
    audit["matches"] = audit.predicted_boss == audit.observed_boss
    # Full-dimension report: act x ascension level x ascension band x build version.
    summary = audit.groupby(["act", "ascension", "ascension_band", "build_version"], dropna=False).agg(samples=("matches", "size"), matches=("matches", "sum")).reset_index()
    summary["agreement_rate"] = summary.matches / summary.samples
    # Fallback-quality gate per act x ascension level.
    gate = audit.groupby(["act", "ascension"], dropna=False).agg(samples=("matches", "size"), matches=("matches", "sum")).reset_index()
    gate["agreement_rate"] = gate.matches / gate.samples
    failures = gate[(gate.samples < minimum_samples) | (gate.agreement_rate < FALLBACK_AGREEMENT_GATE)]
    if audit_path: summary.to_csv(audit_path, index=False)
    if not failures.empty:
        raise ValueError("Boss resolver fallback audit failed: " + failures.to_json(orient="records"))
    frame = pd.DataFrame(rows).drop_duplicates("run_id")
    frame["resolver_version"] = BOSS_RESOLVER_VERSION
    frame.to_parquet(output_path, index=False)
    return summary


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("input_dir"); parser.add_argument("output_path")
    parser.add_argument("--audit-path", required=True); parser.add_argument("--minimum-samples", type=int, default=100)
    args = parser.parse_args()
    build_sidecar(args.input_dir, args.output_path, args.audit_path, args.minimum_samples)
