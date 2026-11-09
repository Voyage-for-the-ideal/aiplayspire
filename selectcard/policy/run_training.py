"""Streaming BC training for run-context-v1; test remains explicitly sealed."""
from collections import Counter
import csv
import json
import os
from pathlib import Path
import time
import torch

from .contracts import FORMAT, MAX_ITEMS, SCALARS, SCALES, digest
from .encoding import FEATURES, build_vocabulary
from .model import SetPolicy
from .profiles import RUN_PROFILE, MIXED_PROFILE
from .shards import ShardDataset, verify_dataset, check_coverage, file_hash
from .training import evaluate, frequency_key, train_epoch


def atomic_save(value, path):
    temporary = path.with_suffix(path.suffix+".tmp")
    torch.save(value, temporary)
    os.replace(temporary, path)


def reports(root, history, validation, dataset):
    report = dict(validation=validation, history=history, data_fingerprint=dataset["fingerprint"],
                  coverage=dataset["report"], promotion="requires paired real-game evaluation; game policy unchanged")
    (root / "metrics.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
    with (root / "curves.csv").open("w", newline="", encoding="utf-8") as stream:
        writer = csv.DictWriter(stream, fieldnames=("epoch", "train_nll", "val_nll", "val_accuracy", "lr", "seconds"))
        writer.writeheader()
        writer.writerows({key: row[key] for key in writer.fieldnames} for row in history)
    if history:
        # Standalone SVG, requiring no plotting dependency on the cluster.
        values = [r[k] for r in history for k in ("train_nll", "val_nll")]
        top = max(max(values), 1e-6)*1.1
        def line(key, color):
            points = " ".join(f"{50+600*i/max(1,len(history)-1):.1f},{330-280*r[key]/top:.1f}" for i,r in enumerate(history))
            return f'<polyline points="{points}" fill="none" stroke="{color}" stroke-width="2"/>'
        svg = '<svg xmlns="http://www.w3.org/2000/svg" width="700" height="390"><rect width="700" height="390" fill="white"/><path d="M50 40V330H660" stroke="black" fill="none"/><text x="50" y="22">NLL (top: '+f'{top:.3f}'+')</text><text x="280" y="375">Epoch</text><text x="80" y="355" fill="blue">train</text><text x="170" y="355" fill="red">validation</text>'+line("train_nll", "blue")+line("val_nll", "red")+'</svg>'
        (root / "curves.svg").write_text(svg, encoding="utf-8")


def train_shards(args):
    from .train import load_checkpoint
    if args.mode != "bc":
        raise ValueError("historical run training supports BC only")
    if args.device.startswith("cuda") and not torch.cuda.is_available():
        raise RuntimeError("CUDA GPU requested but unavailable; refusing CPU fallback")
    manifest = verify_dataset(args.data_dir)
    profile = manifest["input_profile"]
    if profile not in (RUN_PROFILE, MIXED_PROFILE) or manifest["stage"] != args.stage:
        raise ValueError("input profile/stage mismatch; pilot and confirm are independent exports")
    if args.stage == "confirm":
        check_coverage(args.data_dir)
    if args.evaluate:
        if not args.checkpoint:
            raise ValueError("evaluation requires --checkpoint")
        model, artifact = load_checkpoint(args.checkpoint, args.device, expected_profile=profile)
        if artifact["training"]["data_fingerprint"] != manifest["fingerprint"]:
            raise ValueError("evaluation dataset differs from checkpoint")
        data = ShardDataset(args.data_dir, args.evaluation_split)
        try:
            if not len(data):
                raise ValueError("empty evaluation split")
            args.output_dir.mkdir(parents=True, exist_ok=False)
            result = evaluate(model, data, artifact["vocabulary"], batch_size=args.batch_size, device=args.device, frequencies=artifact["frequencies"])
            reports(args.output_dir, [], result, manifest)
        finally:
            data.close()
        return
    if args.checkpoint:
        raise ValueError("BC must start from scratch; use --resume only for the same interrupted run")
    epochs = args.epochs or (8 if args.stage == "pilot" else 10)
    if epochs <= 0 or args.batch_size <= 0:
        raise ValueError("positive epochs/batch-size required")
    if args.epochs_this_run is not None and args.epochs_this_run <= 0:
        raise ValueError("positive --epochs-this-run required")
    package = Path(__file__).resolve().parent
    training_sources = {name: file_hash(package/name) for name in ("run_training.py", "training.py", "encoding.py", "model.py", "profiles.py", "contracts.py", "shards.py", "train.py")}
    training_sources["src/model.py"] = file_hash(package.parent / "src" / "model.py")
    training_code = digest(training_sources)
    config = dict(mode="bc", stage=args.stage, epochs=epochs, batch_size=args.batch_size, seed=args.seed,
                  lr=2.8e-4, weight_decay=.01, gradient_clip=1., scheduler="cosine", input_profile=profile,
                  architecture=dict(d_model=args.d_model, layers=args.layers, heads=args.heads, dropout=.1),
                  data_fingerprint=manifest["fingerprint"], code=manifest["code"],
                  training_code=training_code, device_kind=args.device.split(":")[0],
                  cohort_fraction=manifest["cohort_fraction"], sampler="character-band-task-then-run-v1")
    identity = digest(config)
    train, val = ShardDataset(args.data_dir, "train"), ShardDataset(args.data_dir, "val")
    try:
        if not len(train) or not len(val):
            raise ValueError("empty train/val; no resampling allowed")
        supported_decisions = [row[0] for row in train.connection.execute("SELECT DISTINCT task FROM examples WHERE split='train' ORDER BY task")]
        torch.manual_seed(args.seed)
        if args.resume:
            saved = torch.load(args.resume, map_location=args.device, weights_only=True)
            if saved.get("training_identity") != identity or saved.get("input_profile") != profile:
                raise ValueError("resume requires identical data fingerprint, profile and training configuration")
            marker = args.output_dir / "training.json"
            if not marker.is_file() or json.loads(marker.read_text(encoding="utf-8"))["identity"] != identity:
                raise ValueError("resume output directory is not the original training run")
            model, _ = load_checkpoint(args.resume, args.device, expected_profile=profile)
            vocabulary, frequencies = saved["vocabulary"], saved["frequencies"]
        else:
            args.output_dir.mkdir(parents=True, exist_ok=False)
            (args.output_dir / "training.json").write_text(json.dumps(dict(identity=identity, configuration=config), indent=2), encoding="utf-8")
            vocabulary = build_vocabulary(iter(train))
            frequencies = dict(Counter(frequency_key(row["decision_type"], next(c for c in row["candidates"] if c["action_id"] == row["chosen_action_id"])) for row in train))
            model = SetPolicy(len(vocabulary), **config["architecture"]).to(args.device)
        optimizer = torch.optim.AdamW(model.parameters(), lr=config["lr"], weight_decay=config["weight_decay"])
        scheduler = torch.optim.lr_scheduler.CosineAnnealingLR(optimizer, T_max=epochs)
        start, history, best_nll = 0, [], float("inf")
        if args.resume:
            optimizer.load_state_dict(saved["optimizer"])
            scheduler.load_state_dict(saved["scheduler"])
            start, history, best_nll = saved["epoch"], saved["history"], saved["best_nll"]
            torch.set_rng_state(saved["torch_rng"].cpu())
            if args.device.startswith("cuda"):
                torch.cuda.set_rng_state_all([r.cpu() for r in saved["cuda_rng"]])
        def artifact(epoch, validation):
            return dict(format=FORMAT, input_profile=profile, architecture=model.config, vocabulary=vocabulary,
                        max_items=MAX_ITEMS, scalars=list(SCALARS), scales=list(SCALES), features=list(FEATURES),
                        policy=model.state_dict(), frequencies=frequencies, training=config, training_identity=identity,
                        supported_decisions=supported_decisions, epoch=epoch, validation=validation)
        end = min(epochs, start+args.epochs_this_run) if args.epochs_this_run else epochs
        for epoch in range(start, end):
            timer = time.monotonic()
            loss = train_epoch(model, train, vocabulary, optimizer, batch_size=args.batch_size, seed=args.seed+epoch, device=args.device)
            validation = evaluate(model, val, vocabulary, batch_size=args.batch_size, device=args.device, frequencies=frequencies)
            history.append(dict(epoch=epoch+1, train_nll=loss, val_nll=validation["all"]["nll"], val_accuracy=validation["all"]["accuracy"],
                                lr=optimizer.param_groups[0]["lr"], seconds=time.monotonic()-timer, validation=validation))
            scheduler.step()
            value = artifact(epoch+1, validation)
            if validation["all"]["nll"] < best_nll:
                best_nll = validation["all"]["nll"]
                atomic_save(value, args.output_dir / "best.pt")
            value.update(optimizer=optimizer.state_dict(), scheduler=scheduler.state_dict(), history=history, best_nll=best_nll,
                         torch_rng=torch.get_rng_state(), cuda_rng=torch.cuda.get_rng_state_all() if args.device.startswith("cuda") else [])
            atomic_save(value, args.output_dir / "last.pt")
            if epoch+1 == epochs:
                atomic_save(artifact(epoch+1, validation), args.output_dir / "policy.pt")
            reports(args.output_dir, history, validation, manifest)
            print(json.dumps({k:v for k,v in history[-1].items() if k != "validation"}), flush=True)
        if end == epochs:
            best_model, best = load_checkpoint(args.output_dir / "best.pt", args.device, expected_profile=profile)
            best_validation = evaluate(best_model, val, vocabulary, batch_size=args.batch_size, device=args.device, frequencies=frequencies)
            (args.output_dir / "best-validation.json").write_text(json.dumps(dict(epoch=best["epoch"], metrics=best_validation), indent=2), encoding="utf-8")
    finally:
        train.close()
        val.close()
