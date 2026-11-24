"""Compare two historical BC policies on the candidate's fixed A15+ validation rows."""
import argparse
from collections import Counter, defaultdict
from contextlib import closing
import json
from pathlib import Path
import sqlite3

import numpy as np
import torch
from torch.nn import functional as F

from .contracts import split_for
from .encoding import encode_batch
from .profiles import RUN_PROFILE
from .shards import REWARD_TASKS, ShardDataset, ascension_band, file_hash, read_dataset_manifest, verify_dataset
from .train import load_checkpoint


SKIP_KINDS = {"skip", "leave", "cancel"}


def check_baseline_seeds(data_dir, training, validation_seeds):
    """Only the original manifest and its bound index are needed for this check."""
    if data_dir is None:
        return dict(status="unverified", reason="baseline_data_dir_not_supplied")
    index = (Path(data_dir) / "index.sqlite").resolve()
    try:
        manifest = read_dataset_manifest(data_dir)
        if manifest["fingerprint"] != training["data_fingerprint"] or manifest["input_profile"] != RUN_PROFILE:
            raise ValueError("baseline dataset does not match its checkpoint")
        if file_hash(index) != manifest["files"]["index.sqlite"]:
            raise ValueError("baseline index fingerprint mismatch")
        with closing(sqlite3.connect(index.as_uri()+"?mode=ro", uri=True)) as db:
            overlap, checked = 0, 0
            for (seed,) in db.execute("SELECT DISTINCT seed_group FROM examples WHERE split='train'"):
                checked += 1
                overlap += seed in validation_seeds
        if not checked:
            raise ValueError("baseline index has no training seeds")
    except (OSError, ValueError, KeyError, TypeError, sqlite3.Error) as exc:
        return dict(status="unverified", reason=str(exc))
    return dict(status="overlap" if overlap else "verified_disjoint",
                baseline_training_seed_groups=checked, overlapping_seed_groups=overlap)


def a20_interval(clusters, *, seed=42, samples=5000):
    """Paired decision-weighted delta NLL, resampling entire seed groups."""
    n = len(clusters)
    if not n:
        return dict(decisions=0, seed_groups=0, delta_nll=None, ci95=None)
    sums, counts = np.asarray([clusters[k] for k in sorted(clusters)], dtype=np.float64).T
    delta = float(sums.sum() / counts.sum())
    interval = None
    if n > 1:
        rng = np.random.default_rng(seed)
        draws = np.empty(samples)
        size = max(1, min(64, 262144 // n))
        for start in range(0, samples, size):
            indices = rng.integers(n, size=(min(size, samples - start), n))
            draws[start:start+len(indices)] = sums[indices].sum(axis=1) / counts[indices].sum(axis=1)
        interval = np.quantile(draws, [.025, .975]).tolist()
    return dict(decisions=int(counts.sum()), seed_groups=n, delta_nll=delta, ci95=interval)


def score_batch(model, artifact, rows, device):
    batch = encode_batch(rows, artifact["vocabulary"], device=device)
    logits = model(batch)
    losses = F.cross_entropy(logits, batch["labels"], reduction="none")
    if not torch.isfinite(losses).all():
        raise ValueError("nonfinite validation loss")
    fully_known = batch["legal"] & ~batch["candidate_ids"].eq(1).any(dim=-1)
    return zip(losses.tolist(), logits.argmax(dim=1).tolist(), fully_known.sum(dim=1).tolist())


@torch.inference_mode()
def compare_checkpoints(data_dir, baseline, candidate, *, baseline_data_dir=None, batch_size=512, device="cpu"):
    if batch_size <= 0:
        raise ValueError("positive batch size required")
    manifest = verify_dataset(data_dir)
    if manifest["input_profile"] != RUN_PROFILE or manifest.get("min_ascension") != 15 or manifest.get("include_map"):
        raise ValueError("comparison requires historical reward-only data exported with min_ascension=15")
    models = {}
    for name, path in (("baseline", baseline), ("candidate", candidate)):
        model, artifact = load_checkpoint(path, device, expected_profile=RUN_PROFILE)
        if artifact["training"]["mode"] != "bc" or not set(REWARD_TASKS) <= set(artifact["supported_decisions"]):
            raise ValueError(f"{name} must be a BC policy trained on both reward tasks")
        models[name] = (model.eval(), artifact)
    training = models["candidate"][1]["training"]
    if (training["data_fingerprint"] != manifest["fingerprint"] or training.get("min_ascension") != 15
            or training["stage"] != manifest["stage"]):
        raise ValueError("candidate checkpoint does not match the A15+ dataset")

    counts, totals = defaultdict(Counter), defaultdict(lambda: defaultdict(Counter))
    clusters, validation_seeds = defaultdict(lambda: [0., 0]), set()
    data = ShardDataset(data_dir, "val")
    try:
        if not len(data):
            raise ValueError("empty validation split")
        for start in range(0, len(data), batch_size):
            rows = data[start:start+batch_size]
            for row in rows:
                if (not 15 <= row["pre_state"]["ascension"] <= 20 or row["decision_type"] not in REWARD_TASKS
                        or row["split"] != "val" or split_for(row["seed_group"]) != "val"):
                    raise ValueError("invalid A15+ validation row")
            base = score_batch(*models["baseline"], rows, device)
            new = score_batch(*models["candidate"], rows, device)
            for row, base_row, new_row in zip(rows, base, new):
                task, a, seed = row["decision_type"], row["pre_state"]["ascension"], row["seed_group"]
                validation_seeds.add(seed)
                choices = row["candidates"]
                label = next(c for c in choices if c["action_id"] == row["chosen_action_id"])
                band = "ascension_band:" + ascension_band(a)
                for group in ("all", "task:"+task, band, band+"|task:"+task):
                    counts[group]["decisions"] += 1
                    counts[group]["legal_candidates"] += sum(c["legal"] for c in choices)
                    counts[group]["label_skips"] += label["kind"] in SKIP_KINDS
                    for name, (nll, idx, known) in (("baseline", base_row), ("candidate", new_row)):
                        t = totals[group][name]
                        t["nll"] += nll
                        t["correct"] += choices[idx]["action_id"] == label["action_id"]
                        t["skips"] += choices[idx]["kind"] in SKIP_KINDS
                        t["known_candidates"] += known
                if a == 20:
                    clusters[seed][0] += new_row[0] - base_row[0]
                    clusters[seed][1] += 1
    finally:
        data.close()

    groups = {}
    for group, count in sorted(counts.items()):
        n, legal = count["decisions"], count["legal_candidates"]
        groups[group] = dict(decisions=n, legal_candidates=legal, label_skip_rate=count["label_skips"]/n)
        for name, t in totals[group].items():
            groups[group][name] = dict(nll=t["nll"]/n, accuracy=t["correct"]/n, skip_rate=t["skips"]/n,
                                      legal_candidate_vocab_coverage=t["known_candidates"]/legal)
    seed_check = check_baseline_seeds(baseline_data_dir, models["baseline"][1]["training"], validation_seeds)
    return dict(split="val", min_ascension=15, data_fingerprint=manifest["fingerprint"],
                checkpoints={"baseline_sha256": file_hash(baseline), "candidate_sha256": file_hash(candidate)},
                baseline_seed_check=seed_check, validation_seed_groups=len(validation_seeds),
                evidence="train_disjoint_for_both" if seed_check["status"] == "verified_disjoint" else "descriptive_only",
                groups=groups, a20_paired=a20_interval(clusters),
                interpretation="offline imitation comparison; no causal ascension-filter or gameplay claim")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--data-dir", required=True, type=Path, help="the A15+ candidate's training dataset")
    parser.add_argument("--baseline", required=True, type=Path)
    parser.add_argument("--candidate", required=True, type=Path)
    parser.add_argument("--baseline-data-dir", type=Path, help="optional original baseline manifest and SQLite index")
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--batch-size", type=int, default=512)
    parser.add_argument("--device", default="cpu")
    args = parser.parse_args()
    output = args.output.resolve()
    for source in (args.data_dir, args.baseline_data_dir):
        if source and (output == source.resolve() or source.resolve() in output.parents):
            parser.error("output must be outside input dataset directories")
    if output.exists():
        parser.error("output already exists")
    report = compare_checkpoints(args.data_dir, args.baseline, args.candidate,
                                 baseline_data_dir=args.baseline_data_dir, batch_size=args.batch_size, device=args.device)
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("x", encoding="utf-8") as stream:
        json.dump(report, stream, indent=2)


if __name__ == "__main__":
    main()
