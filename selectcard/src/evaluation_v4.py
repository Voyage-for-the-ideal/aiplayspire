"""Paired, run-clustered V4 evaluation exports and promotion reports."""

import argparse
import hashlib
import json
from itertools import zip_longest
from pathlib import Path

import numpy as np
import torch

try:
    from .experiments import selected_run, write_json
except ImportError:
    from experiments import selected_run, write_json


def fingerprint(value):
    return hashlib.sha256(json.dumps(value, sort_keys=True, separators=(",", ":")).encode()).hexdigest()


class EvaluationRecorder:
    """Sequential-loader export; row losses retain exact macro-NLL denominators."""
    def __init__(self, path, header, dataset):
        self.handle = Path(path).open("x", encoding="utf-8")
        self.header = header
        self.dataset = dataset
        self.offset = 0
        self.handle.write(json.dumps({"header": header}) + "\n")

    def identities(self, count):
        return [self.dataset.identity(i) for i in range(self.offset, self.offset + count)]

    def add(self, probabilities, targets, masks, weights, probe_counts, probe_violations):
        p = np.clip(np.asarray(probabilities, dtype=np.float64), 1e-7, 1 - 1e-7)
        targets = np.asarray(targets, dtype=np.float64)
        elements = -(targets * np.log(p) + (1 - targets) * np.log1p(-p))
        weighted_masks = masks * np.asarray(weights)[:, None]
        for i, (run_id, sample_id, character) in enumerate(self.identities(len(p))):
            row = {
                "run_id": run_id, "sample_id": sample_id, "character": character,
                "label_fingerprint": fingerprint([targets[i].tolist(), masks[i].tolist()]),
                "numerator": (elements[i] * weighted_masks[i]).tolist(),
                "denominator": weighted_masks[i].tolist(),
                "probe_count": int(probe_counts[i]), "probe_violations": int(probe_violations[i]),
            }
            self.handle.write(json.dumps(row, allow_nan=False) + "\n")
        self.offset += len(p)

    def close(self):
        self.handle.close()


def mechanism_counts(model, observed, seq, upgrades, counts, globals_, characters,
                     bosses, floors, context, key_labels, key_masks, egg_ids, eligible):
    """Free observed Egg/key removal probes on a fixed run-hash subset.

    Only observed known keys are compared. Unknown Sapphire is never promoted
    to known, and inferred behavior-policy labels are never used as rankings.
    """
    try:
        from .train import _forward_value_model, scalar_state_value
    except ImportError:
        from train import _forward_value_model, scalar_state_value
    totals = torch.zeros(len(seq), dtype=torch.long, device=seq.device)
    violations = torch.zeros_like(totals)
    value = scalar_state_value(observed, floors)
    for token_id in egg_ids:
        keep = seq.eq(token_id).any(1) & eligible
        if not keep.any():
            continue
        changed = [t[keep].clone() for t in (seq, upgrades, counts)]
        positions = changed[0].eq(token_id).long().argmax(1)
        for tensor in changed:
            tensor[torch.arange(len(positions), device=seq.device), positions] = 0
        local_context = tuple(t[keep] for t in context) if context is not None else None
        other = _forward_value_model(model, *changed, globals_[keep], characters[keep], bosses[keep], local_context)
        totals[keep] += 1
        violations[keep] += (scalar_state_value(other, floors[keep]) > value[keep] + 1e-6).long()
    if context is not None:
        for key in range(3):
            keep = key_labels[:, key].gt(0.5) & key_masks[:, key].gt(0.5) & eligible
            if not keep.any():
                continue
            altered = context[0][keep].clone()
            altered[:, key] = 0
            altered[:, 7] = (altered[:, :3] * altered[:, 3:6]).sum(1) / 3
            if key == 1:
                altered[:, 6] = context[2][keep].bitwise_and(2).ne(0).any(1).float()
            local = (altered, *(t[keep] for t in context[1:]))
            other = _forward_value_model(model, seq[keep], upgrades[keep], counts[keep],
                                         globals_[keep], characters[keep], bosses[keep], local)
            totals[keep] += 1
            violations[keep] += (scalar_state_value(other, floors[keep]) > value[keep] + 1e-6).long()
    return totals.cpu().numpy(), violations.cpu().numpy()


def _score(numerator, denominator, objective):
    include = denominator[:20] > 0
    if "active_hazards" in objective:
        expected = np.zeros(20, dtype=bool)
        expected[objective["active_hazards"]] = True
        if not include[expected].all():
            return None
        include = expected
    if not include.any() or denominator[20] <= 0:
        return None
    entropy = np.asarray(objective["entropy"])
    hazard = np.mean(numerator[:20][include] / denominator[:20][include] / entropy[:20][include])
    heart = numerator[20] / denominator[20] / entropy[20]
    weight = objective["heart_weight"]
    return float((hazard + weight * heart) / (1 + weight))


def _export_headers(paths):
    headers = {}
    for path in paths:
        with Path(path).open(encoding="utf-8") as handle:
            header = json.loads(next(handle))["header"]
        seed = header["seed"]
        if seed in headers:
            raise ValueError(f"Duplicate seed export: {seed}")
        headers[seed] = (header, path)
    return headers


def compare_exports(baseline_paths, candidate_paths, repetitions=2000, seed=42):
    """Resample whole run IDs; all snapshots and paired seeds stay together."""
    if repetitions < 100:
        raise ValueError("At least 100 bootstrap repetitions are required")
    bh = _export_headers(baseline_paths)
    ch = _export_headers(candidate_paths)
    if not bh or set(bh) != set(ch):
        raise ValueError("Paired exports must contain identical seeds")
    contracts = [entry[0]["comparison_contract"] for entry in bh.values()]
    if any(c != contracts[0] for c in contracts):
        raise ValueError("Seeds must use the same comparison contract")
    groups = {}
    for experiment_seed in sorted(bh):
        left_header, left_path = bh[experiment_seed]
        right_header, right_path = ch[experiment_seed]
        if left_header["comparison_contract"] != right_header["comparison_contract"]:
            raise ValueError("Evaluation data/artifacts/objective differ")
        # Stream paired sequential-loader exports; memory scales with run count,
        # not all snapshots times all random seeds.
        with Path(left_path).open(encoding="utf-8") as lf, Path(right_path).open(encoding="utf-8") as rf:
            next(lf)
            next(rf)
            for ll, rr in zip_longest(lf, rf):
                if ll is None or rr is None:
                    raise ValueError("Paired exports must contain identical samples")
                left, right = json.loads(ll), json.loads(rr)
                if any(left[k] != right[k] for k in (
                    "sample_id", "run_id", "label_fingerprint", "denominator", "probe_count"
                )):
                    raise ValueError("Paired labels, samples, weights, or probe coverage differ")
                row = groups.setdefault(left["run_id"], np.zeros((2, 44), dtype=np.float64))
                for index, record in enumerate((left, right)):
                    num, den = np.asarray(record["numerator"]), np.asarray(record["denominator"])
                    if num.shape != (21,) or den.shape != (21,) or not np.isfinite(num).all() or not np.isfinite(den).all() or (den < 0).any() or (num < 0).any():
                        raise ValueError("Invalid exported sufficient statistics")
                    if not 0 <= record["probe_violations"] <= record["probe_count"]:
                        raise ValueError("Invalid probe counts")
                    row[index, :21] += num
                    row[index, 21:42] += den
                    row[index, 42] += record["probe_violations"]
                    row[index, 43] += record["probe_count"]
    if not groups:
        raise ValueError("Evaluation exports have no samples")
    values = np.stack(list(groups.values()))
    objective = dict(contracts[0]["objective"])
    objective["active_hazards"] = np.flatnonzero(values[:, 0, 21:41].sum(0) > 0).tolist()

    def differences(total, bootstrap=False):
        scores = [_score(v[:21], v[21:42], objective) for v in total]
        if any(v is None for v in scores):
            return None
        if bootstrap and (total[:, 43] == 0).any():
            return None
        probe = total[:, 42] / np.maximum(total[:, 43], 1)
        return scores[1] - scores[0], probe[1] - probe[0]

    rng = np.random.default_rng(seed)
    samples = []
    if len(groups) >= 2:
        for _ in range(repetitions):
            draw = rng.integers(0, len(values), size=len(values))
            delta = differences(values[draw].sum(0), bootstrap=True)
            if delta is not None:
                samples.append(delta)
    total = values.sum(0)
    point = differences(total)
    if point is None:
        raise ValueError("Exports have no supervised hazard/Heart targets")
    intervals = np.quantile(samples, [0.025, 0.975], axis=0) if samples else None
    full_confirmation = set(bh) == {42, 43, 44} and contracts[0]["run_fraction"] == 1.0 and contracts[0]["split"] == "val" and all(h[0].get("completed_epochs", 0) >= 20 and h[0].get("stage") == "confirm" for h in (*bh.values(), *ch.values()))
    probe_evidence = total[0, 43] > 0
    enough = intervals is not None and len(samples) >= repetitions * 0.95 and probe_evidence
    improve = bool(enough and intervals[1, 0] < 0)
    worse = bool(enough and intervals[0, 1] > 0)
    return {
        "run_clusters": len(groups), "seeds": sorted(bh), "bootstrap_seed": seed,
        "valid_bootstraps": len(samples), "repetitions": repetitions,
        "baseline_relative_value_nll": _score(total[0, :21], total[0, 21:42], objective),
        "candidate_relative_value_nll": _score(total[1, :21], total[1, 21:42], objective),
        "nll_delta": point[0], "probe_violation_rate_delta": point[1],
        "nll_delta_ci95": intervals[:, 0].tolist() if intervals is not None else None,
        "probe_delta_ci95": intervals[:, 1].tolist() if intervals is not None else None,
        "probe_comparisons": int(total[0, 43]),
        "promote": bool(full_confirmation and improve and not worse),
        "status": "confirmed" if full_confirmation and enough else "insufficient_confirmation",
        "rule": "paired-run-cluster-ci95-nll-upper<0-and-probe-not-significantly-worse",
    }


def evaluate_checkpoint(checkpoint_path, data_dir, output_dir, split="val", device_name="auto"):
    try:
        from .config import Config
    except ImportError:
        from config import Config
    names = ("HEART_HEAD_LOSS_WEIGHT", "BASELINE_ENTROPY_FLOOR", "KEY_HEAD_LOSS_WEIGHT")
    previous = {name: getattr(Config, name) for name in names}
    try:
        return _evaluate_checkpoint(checkpoint_path, data_dir, output_dir, split, device_name)
    finally:
        for name, value in previous.items():
            setattr(Config, name, value)


def _evaluate_checkpoint(checkpoint_path, data_dir, output_dir, split="val", device_name="auto"):
    """Evaluate using only checkpoint-frozen model, tokenizer and fit artifacts."""
    from torch.utils.data import DataLoader
    try:
        from .checkpointing import load_checkpoint
        from .config import Config
        from .dataset import STSDataset, load_dataset_manifest
        from .model import STSValueNetwork
        from .train import evaluate, EGG_RELIC_IDS, _numpy_entropy
    except ImportError:
        from checkpointing import load_checkpoint
        from config import Config
        from dataset import STSDataset, load_dataset_manifest
        from model import STSValueNetwork
        from train import evaluate, EGG_RELIC_IDS, _numpy_entropy
    if split not in {"val", "test"}:
        raise ValueError("Evaluation split must be val or explicitly selected test")
    checkpoint, vocabulary, encoder = load_checkpoint(checkpoint_path)
    metadata = checkpoint.get("metadata", {})
    objective = metadata.get("training_objective", {})
    statistics = metadata.get("baseline_statistics")
    weights = metadata.get("dataset", {}).get("difficulty_weights")
    if statistics is None or weights is None:
        raise ValueError("Checkpoint is missing frozen evaluation statistics/difficulty weights")
    Config.HEART_HEAD_LOSS_WEIGHT = objective["heart_loss_weight"]
    Config.BASELINE_ENTROPY_FLOOR = objective["baseline_entropy_floor"]
    Config.KEY_HEAD_LOSS_WEIGHT = objective["key_loss_weight"]
    fraction = metadata.get("experiment", {}).get("settings", {}).get("RUN_FRACTION", 1.0)
    if split == "test":
        fraction = 1.0
    architecture = checkpoint["model_config"]
    dataset = STSDataset(data_dir, vocabulary, encoder, split=split,
                         max_seq_len=architecture["item_token_budget"],
                         max_upgrade=architecture["max_upgrade"], max_count=architecture["max_count"],
                         use_map_context=architecture["use_map_context"], run_fraction=fraction)
    manifest = load_dataset_manifest(data_dir)
    shard_identity = [(Path(p).name, Path(p).stat().st_size, Path(p).stat().st_mtime_ns)
                      for p in sorted(Path(data_dir).glob("*_valid_chunk_*.parquet"))]
    if device_name == "auto":
        device_name = "cuda" if torch.cuda.is_available() else "cpu"
    if device_name == "cuda" and not torch.cuda.is_available():
        raise ValueError("CUDA requested but not available")
    device = torch.device(device_name)
    model = STSValueNetwork(**architecture).to(device)
    model.load_state_dict(checkpoint["model_state_dict"])
    output = Path(output_dir)
    output.mkdir(parents=True, exist_ok=True)
    prediction_path = output / f"{split}_predictions.jsonl"
    report_path = output / f"{split}_metrics.json"
    if prediction_path.exists() or report_path.exists():
        raise FileExistsError("Evaluation output already exists; choose a new output directory")
    if Path(checkpoint_path).resolve().parent == output.resolve():
        raise ValueError("Evaluation outputs must be separate from source checkpoint directory")
    rates = statistics["global"]["rates"]
    header = {
        "experiments": metadata.get("experiment", {}).get("names", ["baseline"]),
        "completed_epochs": len(metadata.get("history", [])),
        "stage": metadata.get("experiment", {}).get("stage"),
        "seed": metadata["seed"], "checkpoint": str(Path(checkpoint_path).resolve()),
        "comparison_contract": {
            "dataset_fingerprint": fingerprint([manifest, shard_identity]),
            "artifact_fingerprint": fingerprint([vocabulary.to_dict(), encoder.to_dict(), statistics, weights]),
            "run_fraction": fraction, "split": split,
            "objective": {"entropy": _numpy_entropy(rates).tolist(),
                          "heart_weight": objective["heart_loss_weight"]},
        },
    }
    recorder = EvaluationRecorder(prediction_path, header, dataset)
    egg_ids = [vocabulary.item2id[n] for n in EGG_RELIC_IDS if n in vocabulary.item2id]
    try:
        metrics = evaluate(model, DataLoader(dataset, batch_size=512, shuffle=False), device,
                           baseline_rates=rates, baseline_statistics=statistics, band_weights=weights,
                           use_map=architecture["use_map_context"], recorder=recorder, egg_token_ids=egg_ids)
    finally:
        recorder.close()
    write_json(report_path, {"header": header, "metrics": metrics,
                             "device": str(device), "torch_version": str(torch.__version__)})
    return metrics


def select_pilot_candidates(paths):
    """One representative per independent family; never selects combinations."""
    families = {
        "capacity": ("depth8", "width256"), "ffn": ("gelu", "swiglu"),
        "normalization": ("rmsnorm",), "map_structure": ("edges", "dag"),
        "compression": ("latent16", "latent32"), "key_auxiliary": ("key0", "key005", "key02"),
        "monotonic": ("monotonic0", "monotonic003", "monotonic01"),
        "coordinates": ("plr",), "pooling": ("cls_mean",), "registers": ("register2", "register4"),
    }
    scores, contracts = {}, []
    for path in paths:
        report = json.loads(Path(path).read_text(encoding="utf-8"))
        header = report["header"]
        names = header["experiments"]
        contract = header["comparison_contract"]
        if header["stage"] != "pilot" or header["seed"] != 42 or header["completed_epochs"] != 8 or contract["run_fraction"] != .01 or contract["split"] != "val":
            raise ValueError("Pilot selection requires completed 8-epoch seed-42 1% validation reports")
        if len(names) != 1 or names[0] in scores:
            raise ValueError("Pilot representatives must be unique independent experiments")
        score = report["metrics"]["relative_value_nll"]
        if score is None or not np.isfinite(score):
            raise ValueError("Invalid pilot score")
        scores[names[0]] = float(score)
        contracts.append(contract)
    if "baseline" not in scores or any(c != contracts[0] for c in contracts):
        raise ValueError("Pilot reports need a baseline and identical comparison contracts")
    representatives = {}
    for family, names in families.items():
        available = [name for name in names if name in scores]
        if available:
            winner = min(["baseline", *available], key=lambda name: (scores[name], name != "baseline", name))
            representatives[family] = {"experiment": winner, "relative_value_nll": scores[winner]}
    return {"baseline_relative_value_nll": scores["baseline"], "representatives": representatives,
            "next_step": "confirm baseline and each non-baseline representative with seeds 42/43/44; no deployment"}


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--baseline", nargs="+")
    parser.add_argument("--candidate", nargs="+")
    parser.add_argument("--pilot-metrics", nargs="+")
    parser.add_argument("--output", required=True)
    parser.add_argument("--repetitions", type=int, default=2000)
    parser.add_argument("--seed", type=int, default=42)
    args = parser.parse_args(argv)
    if args.pilot_metrics:
        if args.baseline or args.candidate:
            parser.error("Pilot selection cannot be mixed with confirmation comparison")
        result = select_pilot_candidates(args.pilot_metrics)
    else:
        if not args.baseline or not args.candidate:
            parser.error("Provide --pilot-metrics or both --baseline and --candidate")
        result = compare_exports(args.baseline, args.candidate, args.repetitions, args.seed)
    write_json(args.output, result)


if __name__ == "__main__":
    main()
