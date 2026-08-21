"""Build/audit the seed-derived visible-boss sidecar without RunReconstructor."""
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


def _events(path):
    opener = gzip.open if path.endswith(".gz") else open
    with opener(path, "rt", encoding="utf-8") as handle:
        payload = json.load(handle)
    for item in payload if isinstance(payload, list) else [payload]:
        yield item.get("event", item)


def _observed_bosses(event):
    """Audit-only: combat telemetry must not influence resolve_run."""
    observed = {}
    for combat in event.get("damage_taken", []):
        field = _BOSS_FLOORS.get(int(combat.get("floor", -1)))
        if field:
            name = canonicalize_boss_name(combat.get("enemies"))
            if name != "UNKNOWN_BOSS": observed[field] = name
    return observed


def build_sidecar(input_dir, output_path, audit_path=None, minimum_samples=100):
    resolver = BossContextResolver(); rows = []; audits = []
    for path in glob.glob(os.path.join(input_dir, "**", "*.json*"), recursive=True):
        for event in _events(path):
            ascension = int(event.get("ascension_level", 0)) if event.get("is_ascension_mode") else 0
            if ascension == 0:
                # Product policy: A0 never uses a real boss identity, even when
                # the seed could resolve one.  Status is recorded so the
                # enrichment join can distinguish it from a resolution gap.
                resolved = {
                    "act1_boss": "NO_BOSS", "act2_boss": "NO_BOSS",
                    "act3_boss": "NO_BOSS", "resolver_version": BOSS_RESOLVER_VERSION,
                    "resolver_status": "a0_no_boss",
                }
            else:
                try:
                    resolved = resolver.resolve_run(event)
                except ValueError:
                    # Rows lacking a usable seed must make the audit gate fail,
                    # rather than being silently omitted from the training sidecar.
                    resolved = {"act1_boss": "UNKNOWN_BOSS", "act2_boss": "UNKNOWN_BOSS", "act3_boss": "UNKNOWN_BOSS", "resolver_version": BOSS_RESOLVER_VERSION, "resolver_status": "missing_seed"}
            rows.append({"run_id": stable_run_id(event), **resolved})
            if ascension >= 1:
                for field, observed in _observed_bosses(event).items():
                    audits.append({
                        "act": field[3], "ascension": ascension,
                        "ascension_band": ascension_band(ascension),
                        "build_version": str(event.get("build_version", "")),
                        "predicted_boss": canonicalize_boss_name(resolved[field]),
                        "observed_boss": observed,
                    })
    audit = pd.DataFrame(audits)
    if audit.empty: raise ValueError("No audit boss combats found; refusing to publish sidecar")
    audit["matches"] = audit.predicted_boss == audit.observed_boss
    # Full-dimension report: act x ascension level x ascension band x build version.
    summary = audit.groupby(["act", "ascension", "ascension_band", "build_version"], dropna=False).agg(samples=("matches", "size"), matches=("matches", "sum")).reset_index()
    summary["agreement_rate"] = summary.matches / summary.samples
    # Acceptance gate per act x ascension level (99.9% agreement, min samples).
    gate = audit.groupby(["act", "ascension"], dropna=False).agg(samples=("matches", "size"), matches=("matches", "sum")).reset_index()
    gate["agreement_rate"] = gate.matches / gate.samples
    failures = gate[(gate.samples < minimum_samples) | (gate.agreement_rate < 0.999)]
    if audit_path: summary.to_csv(audit_path, index=False)
    if not failures.empty:
        raise ValueError("Boss resolver audit failed: " + failures.to_json(orient="records"))
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
