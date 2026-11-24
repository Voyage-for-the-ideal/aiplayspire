"""CPU-only linear resource-calibration smoke; independent of value checkpoints."""
import argparse
import json
from pathlib import Path

import numpy as np
import torch
from torch.nn import functional as F

from .src.live_contract import check_public, file_sha256
from .policy.contracts import split_for

FEATURES = ("hp", "max_hp", "hp_ratio", "floor", "act", "ascension", "elite", "burning",
            "frontload", "defense", "aoe", "scaling", "cycle", "power_dependence", "coverage", "deck_size",
            "ironclad", "silent", "defect", "watcher", "potion_count", "potions_known",
            "burning_blood", "black_blood", "mark_of_bloom")
FORMAT = "routing-calibration-smoke-v1"


def features(row):
    before, target = row["before"], row["target"]
    check_public(before)
    public, resources, profile = before["observable"], before["resources"], before["deck_profile"]
    values = dict(hp=resources["hp"], max_hp=resources["max_hp"],
        hp_ratio=resources["hp"] / resources["max_hp"], floor=before["floor"], act=before["act"],
        ascension=public["ascension"], elite=float(target["symbol"] == "E"), burning=float(target["burning"]))
    values.update({k: profile[k] for k in FEATURES[8:16]})
    for name, character in zip(FEATURES[16:20], ("IRONCLAD", "THE_SILENT", "DEFECT", "WATCHER")):
        values[name] = float(public["character"] == character)
    values["potion_count"] = sum(not p.get("is_empty", False) for p in public["potions"])
    values["potions_known"] = float(public["field_presence"]["potions"])
    relics = {r["id"] for r in public["relics"]}
    for name, relic in zip(FEATURES[-3:], ("Burning Blood", "Black Blood", "Mark of the Bloom")):
        values[name] = float(relic in relics)
    result = np.asarray([values[k] for k in FEATURES], dtype=np.float32)
    if not np.isfinite(result).all():
        raise ValueError("invalid calibration features")
    return result


def load_model(path):
    artifact = json.loads(Path(path).read_text(encoding="utf-8"))
    if artifact.get("format") != FORMAT or artifact.get("features") != list(FEATURES):
        raise ValueError("incompatible calibration artifact")
    for field, shape in (("mean", (len(FEATURES),)), ("scale", (len(FEATURES),)),
                         ("weight", (3, len(FEATURES))), ("bias", (3,))):
        array = np.asarray(artifact[field], dtype=float)
        if array.shape != shape or not np.isfinite(array).all():
            raise ValueError("invalid calibration artifact: " + field)
    if (np.asarray(artifact["scale"]) <= 0).any():
        raise ValueError("nonpositive feature scale")
    return artifact


def predict(artifact, row):
    x = (features(row) - np.asarray(artifact["mean"])) / np.asarray(artifact["scale"])
    out = np.asarray(artifact["weight"]) @ x + np.asarray(artifact["bias"])
    return dict(mean_net_hp_loss=float(out[0]), p90_net_hp_loss=float(out[1]),
                death_probability=float(1 / (1 + np.exp(-np.clip(out[2], -80, 80)))))


def cpu_smoke(rooms_path, output_dir, *, steps=20, seed=42):
    if not 1 <= steps <= 100:
        raise ValueError("smoke requires 1..100 steps")
    rows = [json.loads(line) for line in Path(rooms_path).read_text(encoding="utf-8").splitlines()]
    if any(r["split"] != split_for(r["seed_group"]) for r in rows):
        raise ValueError("calibration seed-group split mismatch")
    for row in rows:
        if row["split"] == "test":
            continue
        if (type(row["died"]) is not bool or row["regression_valid"] is not (not row["died"])
                or row["died"] and row["net_hp_loss"] is not None
                or not row["died"] and not np.isfinite(row["net_hp_loss"])):
            raise ValueError("invalid calibration labels/masks")
    train = [r for r in rows if r["split"] == "train"]
    val = [r for r in rows if r["split"] == "val"]
    if not train or not val or not any(r["regression_valid"] for r in train):
        raise ValueError("calibration smoke requires train survivors and train/val cohorts")
    output = Path(output_dir)
    output.mkdir(parents=True, exist_ok=False)
    x = np.stack([features(r) for r in train])
    mean, scale = x.mean(0), x.std(0).clip(min=1.)
    x = torch.tensor((x - mean) / scale)
    died = torch.tensor([float(r["died"]) for r in train])
    mask = torch.tensor([r["regression_valid"] for r in train], dtype=torch.bool)
    loss_target = torch.tensor([r["net_hp_loss"] if r["regression_valid"] else 0. for r in train])
    torch.manual_seed(seed)
    model = torch.nn.Linear(len(FEATURES), 3).cpu()
    optimizer = torch.optim.AdamW(model.parameters(), lr=.01, weight_decay=.01)
    history = []
    for _ in range(steps):
        optimizer.zero_grad(set_to_none=True)
        out = model(x)
        error = loss_target[mask] - out[mask, 1]
        quantile = torch.maximum(.9 * error, -.1 * error).mean()
        loss = F.mse_loss(out[mask, 0], loss_target[mask]) + quantile + F.binary_cross_entropy_with_logits(out[:, 2], died)
        if not torch.isfinite(loss):
            raise ValueError("nonfinite calibration loss")
        loss.backward()
        optimizer.step()
        history.append(float(loss.detach()))
    artifact = dict(format=FORMAT, features=list(FEATURES), mean=mean.tolist(), scale=scale.tolist(),
        weight=model.weight.detach().tolist(), bias=model.bias.detach().tolist(),
        source_sha256=file_sha256(rooms_path), train_samples=len(train), val_samples=len(val),
        train_deaths=int(died.sum()), seed=seed, steps=steps, device="cpu", promotable=False)
    path = output / "calibration.json"
    path.write_text(json.dumps(artifact, indent=2, allow_nan=False), encoding="utf-8")
    loaded = load_model(path)
    with torch.no_grad():
        expected = model(x).numpy()
    for i, row in enumerate(train):
        result = predict(loaded, row)
        np.testing.assert_allclose([result["mean_net_hp_loss"], result["p90_net_hp_loss"]], expected[i, :2], rtol=1e-5, atol=1e-5)
    predictions = [predict(loaded, row) for row in val]
    probability = np.clip([p["death_probability"] for p in predictions], 1e-7, 1-1e-7)
    actual = np.asarray([r["died"] for r in val], dtype=float)
    errors = [r["net_hp_loss"] - p["mean_net_hp_loss"] for r, p in zip(val, predictions) if r["regression_valid"]]
    quantile_errors = np.asarray([r["net_hp_loss"] - p["p90_net_hp_loss"]
        for r, p in zip(val, predictions) if r["regression_valid"]])
    report = dict(device="cpu", train_samples=len(train), val_samples=len(val), history=history,
        net_hp_mae=float(np.abs(errors).mean()) if errors else None,
        brier=float(np.mean((probability - actual) ** 2)),
        death_log_loss=float(-np.mean(actual*np.log(probability)+(1-actual)*np.log1p(-probability))),
        p90_coverage=float(np.mean(quantile_errors <= 0)) if len(quantile_errors) else None,
        p90_pinball=float(np.maximum(.9*quantile_errors, -.1*quantile_errors).mean()) if len(quantile_errors) else None,
        promotable=False)
    (output / "report.json").write_text(json.dumps(report, indent=2, allow_nan=False), encoding="utf-8")
    return report


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--rooms", type=Path, required=True)
    parser.add_argument("--output-dir", type=Path, required=True)
    parser.add_argument("--steps", type=int, default=20)
    args = parser.parse_args(argv)
    print(json.dumps(cpu_smoke(args.rooms, args.output_dir, steps=args.steps)))


if __name__ == "__main__":
    main()
