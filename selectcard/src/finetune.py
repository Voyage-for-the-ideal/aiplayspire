"""Explicit CPU-only format-12 smoke update; no deployment or full training."""
import argparse
import copy
import json
import math
from pathlib import Path

import torch
from torch.utils.data import DataLoader

from .checkpointing import load_checkpoint, save_checkpoint
from .config import Config
from .dataset import STSDataset, build_training_artifacts, load_dataset_manifest
from .live_contract import file_sha256, fingerprint
from .model import STSValueNetwork
from .train import _split_batch, monotonic_counterfactual_loss, EGG_RELIC_IDS
from .value_loss import value_loss_components


def verify_export(directory):
    directory = Path(directory)
    manifest = load_dataset_manifest(directory)
    if manifest.get("source_schema") != "live-learning-v1":
        raise ValueError("expected an explicit live-learning export")
    actual = {p.name for p in directory.glob("*.parquet")} | {"rooms.jsonl"}
    if actual != set(manifest["files"]):
        raise ValueError("export file inventory differs from manifest")
    for name, expected in manifest["files"].items():
        if Path(name).name != name or file_sha256(directory / name) != expected:
            raise ValueError("export fingerprint mismatch: " + name)
    # Independent train-only recount verifies the new cohort; none of these fitted
    # artifacts replaces the parent checkpoint's vocabulary/normalization/priors.
    _, _, cohort = build_training_artifacts(directory, include_baselines=True, use_cache=False)
    return manifest, cohort


def cpu_smoke(data_dir, init_checkpoint, output_dir, *, steps=2, batch_size=4, scope="head", seed=42):
    if not 1 <= steps <= 100 or batch_size <= 0 or scope not in ("head", "last2"):
        raise ValueError("smoke requires 1..100 steps, positive batch size and head/last2 scope")
    manifest, cohort = verify_export(data_dir)
    parent_hash = file_sha256(init_checkpoint)
    parent, vocabulary, encoder = load_checkpoint(init_checkpoint, map_location="cpu")
    stats = parent["metadata"].get("baseline_statistics")
    weights = parent["metadata"].get("dataset", {}).get("difficulty_weights")
    if (stats is None or weights is None or len(weights) != 6
            or any(not math.isfinite(w) or w < 0 for w in weights) or not any(w > 0 for w in weights)):
        raise ValueError("parent requires frozen training priors and difficulty weights")
    for band, count in manifest["distributions"]["train_valid_samples_by_ascension_band"].items():
        if count and weights[int(band)] == 0:
            raise ValueError("live ascension band has zero weight in parent training contract")
    architecture = parent["model_config"]
    datasets = {split: STSDataset(data_dir, vocabulary, encoder, split,
        max_seq_len=architecture["item_token_budget"], max_upgrade=architecture["max_upgrade"],
        max_count=architecture["max_count"], use_map_context=architecture["use_map_context"],
        resolution_mode=architecture["resolution_mode"]) for split in ("train", "val")}
    if not all(len(d) for d in datasets.values()):
        raise ValueError("smoke requires nonempty train and val")
    output = Path(output_dir)
    output.mkdir(parents=True, exist_ok=False)
    torch.manual_seed(seed)
    model = STSValueNetwork(**architecture).cpu()
    model.load_state_dict(parent["model_state_dict"])
    for parameter in model.parameters():
        parameter.requires_grad_(False)
    trainable_modules = [model.value_head]
    if scope == "last2":
        trainable_modules += list(model.layers[-2:]) + [model.final_norm]
    for module in trainable_modules:
        for parameter in module.parameters():
            parameter.requires_grad_(True)
    parameters = [p for p in model.parameters() if p.requires_grad]
    optimizer = torch.optim.AdamW(parameters, lr=3e-5 if scope == "head" else 1e-5, weight_decay=.01)
    objective = parent["metadata"]["training_objective"]
    setting_map = {"VALUE_LOSS_MODE": "mode", "FAR_LOSS_WEIGHT": "far_loss_weight",
        "MEAN_LOSS_WEIGHT": "mean_loss_weight", "HEART_HEAD_LOSS_WEIGHT": "heart_loss_weight",
        "KEY_HEAD_LOSS_WEIGHT": "key_loss_weight", "MONOTONIC_LOSS_WEIGHT": "monotonic_loss_weight",
        "BASELINE_ENTROPY_FLOOR": "baseline_entropy_floor", "RESOLUTION_MODE": "resolution_mode"}
    previous = {k: getattr(Config, k) for k in setting_map}
    rates = stats["resolution_statistics"][architecture["resolution_mode"]]["rates"]
    band_weights = torch.tensor(weights, dtype=torch.float32)
    egg_ids = [vocabulary.item2id[n] for n in EGG_RELIC_IDS if n in vocabulary.item2id]
    history = []

    def loss_for(batch):
        base, context, keys = _split_batch(batch, architecture["use_map_context"])
        seq, upgrades, counts, globals_, characters, bosses, floors, targets, masks, terminal = base
        options = {}
        if context is not None:
            options = dict(keys_features=context[0], map_rooms=context[1], map_flags=context[2],
                history_rooms=context[3], history_dist=context[4], map_outgoing_mask=context[5], return_keys=True)
        outputs = model(seq, upgrades, counts, globals_, characters, bosses, **options)
        logits, key_logits = outputs if context is not None else (outputs, None)
        components = value_loss_components(logits, targets, masks, floors, globals_, band_weights,
            rates, terminal_floors=terminal, key_logits=key_logits,
            key_labels=keys[0] if keys else None, key_masks=keys[1] if keys else None)
        monotonic = monotonic_counterfactual_loss(model, logits, seq, upgrades, counts, globals_,
            characters, bosses, floors, egg_ids, context=context,
            key_labels=keys[0] if keys else None, key_masks=keys[1] if keys else None)
        return components["total"] + Config.MONOTONIC_LOSS_WEIGHT * monotonic

    try:
        for field, key in setting_map.items():
            setattr(Config, field, objective[key])
        loader = DataLoader(datasets["train"], batch_size=batch_size, shuffle=True,
                            generator=torch.Generator().manual_seed(seed), num_workers=0)
        iterator = iter(loader)
        model.train()
        for step in range(steps):
            try:
                batch = next(iterator)
            except StopIteration:
                iterator = iter(loader)
                batch = next(iterator)
            optimizer.zero_grad(set_to_none=True)
            loss = loss_for(batch)
            if not torch.isfinite(loss):
                raise ValueError("nonfinite smoke loss")
            loss.backward()
            torch.nn.utils.clip_grad_norm_(parameters, 1., error_if_nonfinite=True)
            optimizer.step()
            history.append({"step": step + 1, "loss": float(loss.detach())})
        model.eval()
        with torch.no_grad():
            validation = [float(loss_for(b)) for b in DataLoader(datasets["val"], batch_size=batch_size)]
        if not all(math.isfinite(x) for x in validation):
            raise ValueError("nonfinite validation loss")
        metadata = copy.deepcopy(parent["metadata"])
        metadata["finetune"] = dict(kind="cpu-smoke", device="cpu", scope=scope, steps=steps,
            parent_sha256=parent_hash, dataset_fingerprint=fingerprint(manifest), seed=seed,
            trainable_parameters=[name for name, p in model.named_parameters() if p.requires_grad],
            cohort_training_counts=manifest["training_outcome_counts"],
            cohort_statistics=cohort, history=history, promotable=False,
            note="Synthetic/CPU pipeline verification; no replay mixture or production model selection")
        metadata["parent_training"] = {k: metadata.get(k) for k in ("experiment", "history", "training_config")}
        metadata["experiment"] = {"stage": "cpu-smoke", "names": ["live-value-smoke"]}
        metadata["history"] = []
        metadata["training_config"] = {"epochs": 0, "steps": steps}
        target = output / "smoke.pth"
        save_checkpoint(target, model, architecture, vocabulary, encoder, metadata)
        loaded, _, _ = load_checkpoint(target, map_location="cpu")
        for name, value in model.state_dict().items():
            torch.testing.assert_close(value, loaded["model_state_dict"][name], rtol=0, atol=0)
        if file_sha256(init_checkpoint) != parent_hash:
            raise ValueError("parent checkpoint changed during smoke")
        report = dict(device="cpu", steps=steps, train_samples=len(datasets["train"]),
            val_samples=len(datasets["val"]), validation_loss=sum(validation) / len(validation),
            history=history, parent_sha256=parent_hash, checkpoint_sha256=file_sha256(target), promotable=False)
        (output / "report.json").write_text(json.dumps(report, indent=2, allow_nan=False), encoding="utf-8")
        return report
    finally:
        for field, value in previous.items():
            setattr(Config, field, value)


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--data-dir", type=Path, required=True)
    parser.add_argument("--init-checkpoint", type=Path, required=True)
    parser.add_argument("--output-dir", type=Path, required=True)
    parser.add_argument("--steps", type=int, default=2)
    parser.add_argument("--scope", choices=("head", "last2"), default="head")
    args = parser.parse_args(argv)
    print(json.dumps(cpu_smoke(args.data_dir, args.init_checkpoint, args.output_dir, steps=args.steps, scope=args.scope)))


if __name__ == "__main__":
    main()
