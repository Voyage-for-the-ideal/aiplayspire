"""Explicit BC/AWR jobs; never automatically invoked by the framework."""
import argparse
from collections import Counter
import json
from pathlib import Path
import torch
from .contracts import FORMAT, MAX_ITEMS, SCALARS, SCALES, digest
from .data import load_examples
from .encoding import FEATURES, build_vocabulary
from .model import SetPolicy, HeartValue
from .training import evaluate, frequency_key, is_bc_example, is_awr_example, pilot_member, train_epoch
from .profiles import LIVE_PROFILE, PROFILES


def load_checkpoint(path, device="cpu", *, expected_profile=None):
    value = torch.load(path, map_location=device, weights_only=True)
    if value.get("format") != FORMAT or value.get("max_items") != MAX_ITEMS or value.get("scalars") != list(SCALARS) or value.get("scales") != list(SCALES) or value.get("features") != list(FEATURES):
        raise ValueError("incompatible policy checkpoint; value checkpoints are not policies")
    profile = value.get("input_profile", LIVE_PROFILE)
    if profile not in PROFILES or expected_profile is not None and profile != expected_profile:
        raise ValueError("incompatible policy input profile")
    value["input_profile"] = profile
    model = SetPolicy(len(value["vocabulary"]), **value["architecture"]).to(device)
    model.load_state_dict(value["policy"])
    return model, value


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    data = parser.add_mutually_exclusive_group(required=True)
    data.add_argument("--data", type=Path)
    data.add_argument("--data-dir", type=Path)
    parser.add_argument("--output-dir", type=Path, required=True)
    parser.add_argument("--mode", choices=("bc", "awr"), default="bc")
    parser.add_argument("--stage", choices=("pilot", "confirm"), default="pilot")
    parser.add_argument("--checkpoint", type=Path)
    parser.add_argument("--evaluate", action="store_true")
    parser.add_argument("--evaluation-split", choices=("val", "test"), default="val")
    parser.add_argument("--epochs", type=int)
    parser.add_argument("--batch-size", type=int, default=512)
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument("--device", default="cpu")
    parser.add_argument("--resume", type=Path)
    parser.add_argument("--epochs-this-run", type=int, help="stop at an epoch boundary; resume with unchanged total --epochs")
    parser.add_argument("--d-model", type=int, default=192)
    parser.add_argument("--layers", type=int, default=5)
    parser.add_argument("--heads", type=int, default=4)
    args = parser.parse_args()
    if args.evaluation_split == "test" and not args.evaluate:
        parser.error("test requires explicit --evaluate")
    if (args.mode == "awr" or args.evaluate) and not args.checkpoint:
        parser.error("AWR/evaluation requires a policy checkpoint")
    if args.data_dir:
        from .run_training import train_shards
        train_shards(args)
        return
    if args.resume or args.epochs_this_run:
        parser.error("resume/epoch-boundary pause requires sharded --data-dir")
    torch.manual_seed(args.seed)
    examples = load_examples(args.data)
    eligible = is_bc_example if args.mode == "bc" else is_awr_example
    train = [r for r in examples if r["split"] == "train" and eligible(r)]
    val = [r for r in examples if r["split"] == args.evaluation_split and eligible(r)]
    if args.stage == "pilot" and not args.evaluate:
        train = [r for r in train if pilot_member(r["run_id"])]
        val = [r for r in val if pilot_member(r["run_id"])]
    if not val or not args.evaluate and not train:
        parser.error("empty eligible split (pilot needs enough run hashes); no resampling into validation")
    if args.checkpoint:
        model, artifact = load_checkpoint(args.checkpoint, args.device)
        vocabulary = artifact["vocabulary"]
        frequencies = artifact["frequencies"]
    else:
        vocabulary = build_vocabulary(train)
        model = SetPolicy(len(vocabulary)).to(args.device)
        frequencies = dict(Counter(frequency_key(r["decision_type"], next(c for c in r["candidates"] if c["action_id"] == r["chosen_action_id"])) for r in train))
    args.output_dir.mkdir(parents=True, exist_ok=False)
    if args.evaluate:
        report = evaluate(model, val, vocabulary, batch_size=args.batch_size, device=args.device, frequencies=frequencies)
        (args.output_dir / "metrics.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
        return
    critic = None
    history = []
    epochs = args.epochs or (5 if args.mode == "awr" else 8 if args.stage == "pilot" else 10)
    if epochs <= 0 or args.batch_size <= 0:
        parser.error("positive epochs/batch-size required")
    if args.mode == "awr":
        # Input order is chronological trajectory export order; buffer whole runs.
        train.sort(key=lambda row: (row.get("run_started_at", 0), row["run_id"]))
        run_order = list(dict.fromkeys(r["run_id"] for r in train))[-2000:]
        selected = set(run_order)
        train = [r for r in train if r["run_id"] in selected]
        critic = HeartValue(len(vocabulary), **model.config).to(args.device)
        if artifact.get("critic"):
            critic.load_state_dict(artifact["critic"])
        optimizer = torch.optim.AdamW(critic.parameters(), lr=2.8e-4, weight_decay=.01)
        for epoch in range(epochs):
            loss = train_epoch(critic, train, vocabulary, optimizer, batch_size=args.batch_size, seed=args.seed + epoch,
                               value_only=True, device=args.device)
            history.append({"critic_epoch": epoch + 1, "loss": loss})
    lr = 1e-4 if args.mode == "awr" else 2.8e-4
    optimizer = torch.optim.AdamW(model.parameters(), lr=lr, weight_decay=.01)
    scheduler = torch.optim.lr_scheduler.CosineAnnealingLR(optimizer, T_max=epochs)
    for epoch in range(epochs):
        loss = train_epoch(model, train, vocabulary, optimizer, batch_size=args.batch_size, seed=args.seed + epoch,
                           critic=critic, device=args.device)
        scheduler.step()
        history.append({"policy_epoch": epoch + 1, "loss": loss})
        print(json.dumps(history[-1]))
    report = evaluate(model, val, vocabulary, batch_size=args.batch_size, device=args.device, frequencies=frequencies)
    artifact = {"format": FORMAT, "architecture": model.config, "vocabulary": vocabulary,
                "max_items": MAX_ITEMS, "scalars": list(SCALARS), "scales": list(SCALES), "features": list(FEATURES),
                "policy": model.state_dict(), "critic": critic.state_dict() if critic is not None else None,
                "frequencies": frequencies, "training": {"mode": args.mode, "stage": args.stage, "seed": args.seed, "epochs": epochs,
                "lr": lr, "batch_size": args.batch_size, "gamma": 1., "beta": .1, "weight_cap": 20.,
                "data_sha256": digest(args.data.read_text(encoding="utf-8")), "train_runs": len(set(r["run_id"] for r in train)),
                "train_samples": len(train), "train_heart_positives": sum(r["outcome"].get("heart_victory") is True for r in train)},
                "supported_decisions": sorted(set(r["decision_type"] for r in train))}
    with (args.output_dir / "policy.pt").open("xb") as handle:
        torch.save(artifact, handle)
    (args.output_dir / "metrics.json").write_text(json.dumps({"validation": report, "history": history,
                "heart_positive_evidence": artifact["training"]["train_heart_positives"] > 0,
                "promotion": "requires paired real-game evaluation"}, indent=2), encoding="utf-8")


if __name__ == "__main__":
    main()
